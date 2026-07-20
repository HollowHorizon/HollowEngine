package ru.hollowhorizon.hollowengine.common.scripting.nodes

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import ru.hollowhorizon.hollowengine.client.handlers.TickHandler
import ru.hollowhorizon.hollowengine.common.events.tick.TickEvent

/** Runs [block] once when the node starts (after `onLoad` has restored persisted state). */
context(script: NodeScript)
fun onStart(block: suspend CoroutineScope.() -> Unit) {
    script.onStartHandlers += block
}

/** Runs [block] when the node is stopped (its scope is cancelled). */
context(script: NodeScript)
fun onStop(block: () -> Unit) {
    script.coroutineContext.job.invokeOnCompletion { block() }
}

/** Registers a handler that writes persistent data into the node's tag on world save. */
context(script: NodeScript)
fun onSave(block: (SerializationContext) -> Unit) {
    script.onSaveHandlers += block
}

/** Registers a handler that reads persistent data back from the node's tag on load. */
context(script: NodeScript)
fun onLoad(block: (SerializationContext) -> Unit) {
    script.onLoadHandlers += block
}

/**
 * Runs [block] every [every] server ticks while the node is alive.
 */
context(script: NodeScript)
fun onUpdate(every: Ticks = 1.ticks, block: suspend CoroutineScope.() -> Unit) {
    require(every.count > 0) { "onUpdate interval must be > 0, got ${every.count}" }
    TickEvent.Server.subscribe(script) {
        if (TickHandler.serverTick % every.count == 0) script.launch { block() }
    }
}
