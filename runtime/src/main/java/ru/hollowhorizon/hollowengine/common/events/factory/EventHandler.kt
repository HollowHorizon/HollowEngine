package ru.hollowhorizon.hollowengine.common.events.factory

import kotlinx.coroutines.suspendCancellableCoroutine
import ru.hollowhorizon.hollowengine.common.events.Cancellable
import ru.hollowhorizon.hollowengine.common.events.Event
import ru.hollowhorizon.hollowengine.common.events.EventListener
import ru.hollowhorizon.hollowengine.common.events.eventListenerOf
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.coroutines.resume
import kotlin.reflect.KClass
import kotlin.reflect.full.companionObjectInstance

open class EventHandler<T : Event> {
    private val handlers = CopyOnWriteArrayList<EventListener<T>>()

    @Volatile
    var listeners: Array<(T) -> Unit> = emptyArray()
        private set

    fun register(priority: Int = 0, listener: (T) -> Unit): (T) -> Unit {
        handlers.add(eventListenerOf(priority, listener))
        update()
        return listener
    }

    fun register(listener: EventListener<T>): EventListener<T> {
        handlers.add(listener)
        update()
        return listener
    }

    fun unregister(listener: EventListener<T>) {
        if (handlers.remove(listener)) {
            update()
        }
    }

    fun clear() {
        handlers.clear()
        update()
    }

    private fun update() {
        listeners = handlers.sortedByDescending { it.priority }.toTypedArray()
    }

    fun post(event: T): T {
        val currentListeners = listeners
        val cancellable = event as? Cancellable

        for (i in currentListeners.indices) {
            currentListeners[i](event)
            if (cancellable?.isCanceled == true) break
        }
        return event
    }

    companion object {
        private val handlers = HashMap<KClass<*>, EventHandler<*>>()

        @Suppress("UNCHECKED_CAST")
        fun <T : Event> get(type: KClass<T>): EventHandler<T> = handlers.getOrPut(type) {
            type.companionObjectInstance as EventHandler<T>
        } as EventHandler<T>
    }
}

suspend inline fun <reified T : Event> EventHandler<T>.await(
    priority: Int = 0,
    crossinline filter: (T) -> Boolean = { true },
): T = suspendCancellableCoroutine { continuation ->
    val listener: (T) -> Unit = object : EventListener<T> {
        override fun invoke(event: T) {
            if (filter(event)) {
                unregister(this)
                continuation.resume(event)
            }
        }
    }

    register(priority, listener)

    continuation.invokeOnCancellation {
        unregister(listener)
    }
}