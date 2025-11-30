package ru.hollowhorizon.hollowengine.common.components.events

import ru.hollowhorizon.hollowengine.common.components.Component
import ru.hollowhorizon.hollowengine.common.components.binding.Side
import ru.hollowhorizon.hollowengine.common.components.isClientSide
import ru.hollowhorizon.hollowengine.common.events.*
import java.lang.invoke.LambdaMetafactory
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.lang.reflect.Method
import java.util.function.Consumer

object ComponentEventSubscriber {
    fun Component<*>.setupEvents() {
        val handles = MethodHandles.lookup()
        val listeners = this.javaClass.declaredMethods
            .filter { method -> method.isAnnotationPresent(SubscribeEvent::class.java) }
            .map { method ->
                EventInstance(
                    if (ClientEvent::class.java.isAssignableFrom(method.parameterTypes[0])) Side.CLIENT else Side.BOTH,
                    method.parameterTypes[0] as Class<Event>,
                    handles.createEventListener(
                        method,
                        this
                    ) {
                        this.owner
                    }
                )
            }

        if (listeners.isEmpty()) return

        onAttach {
            listeners.forEach { instance ->
                instance.side.whenOn(isClientSide) {
                    EventBus.registerNoInline(instance.event, instance.handler)
                }
            }
        }

        onDetach {
            listeners.forEach { instance ->
                instance.side.whenOn(isClientSide) {
                    EventBus.unregisterNoInline(instance.event, instance.handler)
                }
            }
        }
    }

    private fun MethodHandles.Lookup.createEventListener(
        method: Method,
        target: Any,
        componentOwner: () -> Any?, // Добавили аргумент для фильтрации
    ): EventListener<Event> {
        try {
            val methodHandle = unreflect(method)
            // LambdaMetafactory остается без изменений
            val callSite = LambdaMetafactory.metafactory(
                this,
                "accept",
                MethodType.methodType(Consumer::class.java, target.javaClass),
                MethodType.methodType(Void.TYPE, Any::class.java),
                methodHandle,
                MethodType.methodType(Void.TYPE, method.parameterTypes[0])
            )

            val priority = method.getAnnotation(SubscribeEvent::class.java).priority

            // Создаем consumer через Factory, как и раньше
            val eventHandle = callSite.target.bindTo(target).invokeWithArguments() as Consumer<Event>

            return object : EventListener<Event> {
                override val priority = priority

                override fun onEvent(event: Event) {
                    if (event is ComponentDispatcherEvent<*>) {
                        if (event.owner != componentOwner()) return
                    }

                    eventHandle.accept(event)
                }
            }
        } catch (t: Throwable) {
            throw IllegalStateException("Error while registering $method", t)
        }
    }

    private class EventInstance(
        val side: Side,
        val event: Class<Event>,
        val handler: EventListener<Event>,
    )
}