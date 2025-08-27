package ru.hollowhorizon.hollowengine.common.events

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.reflect.KClass


object EventBus {
    val listeners = ConcurrentHashMap<KClass<out Event>, MutableList<EventListener<out Event>>>()

    inline fun <reified T : Event> register(listener: EventListener<T>) {
        val list = listeners.getOrPut(T::class, ::CopyOnWriteArrayList)
        list.add(listener)
        list.sortBy { it.priority }
    }

    fun registerNoInline(type: Class<Event>, listener: EventListener<Event>) {
        val list = listeners.getOrPut(type.kotlin, ::CopyOnWriteArrayList)
        list.add(listener)
        list.sortBy { it.priority }
    }

    inline fun <reified T : Event> unregister(listener: EventListener<T>) {
        listeners[T::class]?.remove(listener)
    }


    @Suppress("UNCHECKED_CAST")
    @JvmStatic
    fun <T : Event> post(event: T) {
        val cancelable = event as? Cancelable

        listeners[event::class]?.forEach {
            (it as EventListener<T>).onEvent(event)
            if (cancelable?.isCanceled == true) return
        }
    }
}

suspend inline fun <reified T : Event> awaitEvent(crossinline isValidCondition: (T) -> Boolean = { true }): T {
    var listener: EventListener<T>? = null

    val result: T = suspendCoroutine { continuation ->
        listener = EventListener { event ->
            if (isValidCondition(event)) continuation.resume(event)
        }
        EventBus.register(listener ?: return@suspendCoroutine)
    }

    EventBus.unregister(listener!!)


    return result
}

inline fun <reified T : Event> eventFlow(): Flow<T> = callbackFlow {
    val listener = EventListener<T> {
        trySend(it)
    }
    EventBus.register(listener)
    awaitClose { EventBus.unregister(listener) }
}