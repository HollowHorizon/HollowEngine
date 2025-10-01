package ru.hollowhorizon.hollowengine.common.graph

import kotlinx.coroutines.Job

class State(
    val name: String,
    val onEnter: suspend StateControl.() -> Unit,
    val onUpdate: suspend StateControl.() -> Unit,
    val onExit: suspend CancelableStateControl.() -> Unit,
    private val events: Set<EventHandler<*>>,
) {

    internal var enterJob: Job? = null
    internal var updateJob: Job? = null
    internal var exitJob: Job? = null

    var status: Status = Status.ENTER

    fun subscribe() {
        events.forEach { event -> event.subscribe() }
    }

    fun unsubscribe() {
        events.forEach { event -> event.unsubscribe() }
    }

    suspend fun await() {
        enterJob?.join()
        updateJob?.join()
        exitJob?.join()
        events.forEach { it.await() }
    }

    fun cancel() {
        enterJob?.cancel()
        updateJob?.cancel()
        exitJob?.cancel()
        events.forEach { it.cancel() }
    }
}

interface StateControl {
    fun transition(nextState: String)
}

interface CancelableStateControl: StateControl {
    fun cancel()
}

enum class Status {
    ENTER, UPDATE, EXIT, COMPLETE
}