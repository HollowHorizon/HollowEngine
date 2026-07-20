package ru.hollowhorizon.hollowengine.common.events.factory

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.suspendCancellableCoroutine
import ru.hollowhorizon.hollowengine.common.events.Cancellable
import ru.hollowhorizon.hollowengine.common.events.Event
import ru.hollowhorizon.hollowengine.common.events.EventListener
import ru.hollowhorizon.hollowengine.common.events.eventListenerOf
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
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

    open fun register(listener: EventListener<T>): EventListener<T> {
        handlers.add(listener)
        update()
        return listener
    }

    open fun register(scope: CoroutineScope, listener: EventListener<T>): EventListener<T> {
        val job = requireNotNull(scope.coroutineContext[Job]) {
            "Event subscriptions require a CoroutineScope with a Job"
        }
        register(listener)
        job.invokeOnCompletion { unregister(listener) }
        return listener
    }

    fun subscribe(
        scope: CoroutineScope,
        priority: Int = 0,
        listener: (T) -> Unit,
    ): EventListener<T> {
        val registered = eventListenerOf(priority, listener)
        return register(scope, registered)
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

    open fun post(event: T): T {
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
    val isDone = AtomicBoolean(false)

    val listener = object : EventListener<T> {
        override val priority = priority

        override fun invoke(event: T) {
            if (filter(event) && isDone.compareAndSet(false, true)) {
                unregister(this)
                continuation.resume(event)
            }
        }
    }

    register(listener)

    continuation.invokeOnCancellation {
        if (isDone.compareAndSet(false, true)) {
            unregister(listener)
        }
    }
}

context(scope: CoroutineScope)
fun <T : Event> EventHandler<T>.subscribe(priority: Int = 0, listener: (T) -> Unit): EventListener<T> {
    val job = scope.coroutineContext[Job]
        ?: error("CoroutineScope must contain a Job")

    val scopedListener = object : EventListener<T> {
        override val priority = priority

        override fun invoke(event: T) {
            if (job.isActive) {
                listener(event)
            }
        }
    }

    if (!job.isActive) return scopedListener

    register(scopedListener)

    job.invokeOnCompletion {
        unregister(scopedListener)
    }

    return scopedListener
}
