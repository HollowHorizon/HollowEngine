package ru.hollowhorizon.hollowengine.common.events

import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.reflect.Method

fun interface EventListener<T : Event> : (T) -> Unit {
    val priority: Int
        get() = 0

    override operator fun invoke(event: T)
}
fun <T : Event> eventListenerOf(
    priority: Int = 0,
    handler: (T) -> Unit,
): EventListener<T> = object : EventListener<T> {
    override val priority: Int = priority

    override fun invoke(event: T) {
        handler(event)
    }
}
fun MethodHandles.Lookup.createEventListener(
    method: Method,
    target: Any,
): EventListener<Event> {
    return try {
        val handle = unreflectMethod(method).bindTo(target)
        createListener(method, handle)
    } catch (error: Throwable) {
        throw IllegalStateException("Error while registering $method", error)
    }
}

fun MethodHandles.Lookup.createStaticEventListener(
    method: Method,
): EventListener<Event> {
    return try {
        val handle = unreflectMethod(method)
        createListener(method, handle)
    } catch (error: Throwable) {
        throw IllegalStateException("Error while registering $method", error)
    }
}

private fun MethodHandles.Lookup.unreflectMethod(method: Method): MethodHandle {
    return unreflect(method)
}

private fun createListener(
    method: Method,
    handle: MethodHandle,
): EventListener<Event> {
    val priority = method
        .getAnnotation(SubscribeEvent::class.java)
        .priority

    return object : EventListener<Event> {
        override val priority: Int = priority

        override fun invoke(event: Event) {
            handle.invoke(event)
        }
    }
}

