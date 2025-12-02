package ru.hollowhorizon.hollowengine.common.components.events

import ru.hollowhorizon.hollowengine.HollowEngine
import ru.hollowhorizon.hollowengine.common.components.Component
import ru.hollowhorizon.hollowengine.common.components.isClient
import ru.hollowhorizon.hollowengine.common.components.isClientSide
import ru.hollowhorizon.hollowengine.common.events.*
import ru.hollowhorizon.hollowengine.common.utils.JavaHacks

inline fun <reified T : Event> Component<*>.on() = EventHandler(this, T::class.java)

class EventHandler<T : Event>(val component: Component<*>, val eventType: Class<T>) {
    val isClientSideEvent = ClientEvent::class.java.isAssignableFrom(eventType)

    private val filters = mutableListOf<(T) -> Boolean>()
    private var priority = 0

    fun priority(priority: Int) {
        this.priority = priority
    }

    fun filter(filter: (T) -> Boolean) = apply { filters.add(filter) }
    fun clientOnly() = apply { filters.add { component.isClientSide } }
    fun serverOnly() = apply { filters.add { !component.isClientSide } }
    fun onlyOwner(extractor: (T) -> Any?) = apply {
        filters.add { event -> extractor(event) === component.owner }
    }

    fun listen(action: (event: T) -> Unit) = component.apply {
        if (isClientSideEvent && !isClientSide) return@apply

        val listener = eventListenerOf<T>(priority) { event ->
            try {
                // Необходимо чтобы ивент ВСЕГДА срабатывал только на нужной стороне
                // Чтобы не получилось такого, что клиентский обработчик поймал серверное событие или наоборот
                if(event is ComponentDispatcherEvent) {
                    if(event.owner.isClient != this.isClientSide) return@eventListenerOf
                }

                if (filters.all { it(event) }) action(event)
            } catch (e: Exception) {
                HollowEngine.LOGGER.error("Error in component ${component.javaClass.simpleName}: ", e)
            }
        }


        onAttach {
            EventBus.registerNoInline(JavaHacks.forceCast(eventType), JavaHacks.forceCast(listener))
        }

        onDetach {
            EventBus.unregisterNoInline(JavaHacks.forceCast(eventType), JavaHacks.forceCast(listener))
        }
    }
}