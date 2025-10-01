package ru.hollowhorizon.hollowengine.common.events

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.coroutines.resume
import kotlin.reflect.KClass


object EventBus {
    val listeners = ConcurrentHashMap<KClass<out Event>, MutableList<EventListener<out Event>>>()

    inline fun <reified T : Event> register(listener: EventListener<T>) {
        val list = listeners.getOrPut(T::class, ::CopyOnWriteArrayList)
        list.add(listener)
        list.sortByDescending { it.priority }
    }

    fun registerNoInline(type: Class<Event>, listener: EventListener<Event>) {
        val list = listeners.getOrPut(type.kotlin, ::CopyOnWriteArrayList)
        list.add(listener)
        list.sortByDescending { it.priority }
    }

    inline fun <reified T : Event> unregister(listener: EventListener<T>) {
        listeners[T::class]?.remove(listener)
    }

    fun unregisterNoInline(type: Class<Event>, listener: EventListener<Event>) {
        listeners[type.kotlin]?.remove(listener)
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

suspend inline fun <reified T : Event> await(): T =
    suspendCancellableCoroutine { continuation ->
        val listener = object : EventListener<T> {
            override fun onEvent(event: T) {
                EventBus.unregister(this)
                continuation.resume(event)
            }
        }

        EventBus.register(listener)

        continuation.invokeOnCancellation {
            EventBus.unregister(listener)
        }
    }

inline fun <reified T : Event> eventFlow(): Flow<T> = callbackFlow {
    val listener = EventListener<T> {
        trySend(it)
    }
    EventBus.register(listener)
    awaitClose { EventBus.unregister(listener) }
}