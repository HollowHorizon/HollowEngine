package ru.hollowhorizon.hollowengine.common.graph

import kotlinx.coroutines.CoroutineScope
import net.minecraft.client.Minecraft
import ru.hollowhorizon.hollowengine.common.coroutines.coroutineScope
import ru.hollowhorizon.hollowengine.common.events.*
import ru.hollowhorizon.hollowengine.common.utils.currentServer


inline fun <reified E : Event> eventHandlerOf(
    scope: CoroutineScope? = null,
    priority: Int = 0,
    allowRepeats: Boolean = false,
    noinline listener: StateControl.(E) -> Unit,
): EventHandler<E> {
    if (scope == null) return EventHandler(listener, E::class.java, allowRepeats, priority)
    return ScopedEventHandler(scope, listener, E::class.java, allowRepeats, priority)
}


class ScopedEventHandler<T : Event>(
    val scope: CoroutineScope,
    listener: StateControl.(T) -> Unit,
    type: Class<T>,
    allowRepeats: Boolean = false,
    priority: Int = 0,
) : EventHandler<T>(
    listener, type, allowRepeats, priority
) {
    override val currentScope get() = scope
}

open class EventHandler<T : Event>(
    listener: StateControl.(T) -> Unit,
    val type: Class<T>,
    allowRepeats: Boolean = false,
    priority: Int = 0,
) {
    lateinit var graph: Graph

    protected open val currentScope get() = if (ClientEvent::class.java.isAssignableFrom(type)) Minecraft.getInstance().coroutineScope else currentServer.coroutineScope
    private var eventListener = eventListenerOf(priority) { event: T ->
        graph.listener(event)
    }

    fun subscribe() {
        EventBus.registerNoInline(type as Class<Event>, eventListener as EventListener<Event>)
    }

    fun unsubscribe() {
        EventBus.unregisterNoInline(type as Class<Event>, eventListener as EventListener<Event>)
    }

    suspend fun await() {
        //job?.join()
    }

    fun cancel() {
        //job?.cancel()
    }
}