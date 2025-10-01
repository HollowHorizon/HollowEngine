package ru.hollowhorizon.hollowengine.common.graph

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import ru.hollowhorizon.hollowengine.HollowEngine
import ru.hollowhorizon.hollowengine.common.events.Event
import ru.hollowhorizon.hollowengine.common.graph.StateControl

class Graph(
    private val states: Array<State>,
    private val globalEvents: Set<EventHandler<*>>,
    initialState: Int,
) : CancelableStateControl {
    init {
        require(initialState in states.indices) { "Initial state index is out of bounds!" }
    }

    var exitCanceled = false
    var currentIndex = initialState
        private set
    val currentState: State get() = states[currentIndex]

    var nextState: Int? = null

    fun start() {
        globalEvents.forEach { event -> event.subscribe() }
        currentState.status = Status.ENTER
    }

    fun stop() {
        globalEvents.forEach { event -> event.unsubscribe() }
    }

    fun update(scope: CoroutineScope) {
        currentState.apply {
            when (status) {
                Status.ENTER -> {
                    if (enterJob?.isActive != true) {
                        subscribe()
                        enterJob = scope.launch {
                            try {
                                onEnter()
                            } catch (e: Exception) {
                                HollowEngine.LOGGER.error("Error in state $name on enter: ", e)
                            } finally {
                                status = Status.UPDATE
                            }
                        }
                    }
                }

                Status.UPDATE -> {
                    if (updateJob?.isActive != true) {
                        updateJob = scope.launch {
                            onUpdate()
                        }
                    }
                }

                Status.EXIT -> {
                    if (exitJob?.isActive != true) {
                        exitJob = scope.launch {
                            try {
                                onExit()
                            } catch (e: Exception) {
                                HollowEngine.LOGGER.error("Error in state $name on exit: ", e)
                            } finally {
                                if (exitCanceled) {
                                    status = Status.UPDATE
                                    nextState = null
                                    exitCanceled = false
                                } else {
                                    unsubscribe()
                                    nextState?.let { index ->
                                        currentIndex = index
                                        nextState = null
                                        status = Status.ENTER
                                    } ?: run {
                                        status = Status.COMPLETE
                                    }
                                }
                            }
                        }
                    }
                }

                Status.COMPLETE -> {
                    // Do nothing or handle completion
                }
            }
        }
    }

    suspend fun await() {
        currentState.apply {
            enterJob?.join()
            updateJob?.join()
            exitJob?.join()
        }
    }

    override fun transition(nextState: String) {
        currentState.status = Status.EXIT
        currentState.enterJob?.cancel()
        currentState.updateJob?.cancel()
        val next = states.indexOfFirst { it.name == nextState }
        if (next != -1) this@Graph.nextState = next
        else HollowEngine.LOGGER.warn("State $nextState not found!")
    }

    override fun cancel() {
        exitCanceled = true
    }

    val isCompleted get() = currentState.status == Status.COMPLETE
}

class GraphContext {
    private val states = HashSet<State>()

    @PublishedApi
    internal val globalEvents = HashSet<EventHandler<*>>()
    private var initialState: String? = null

    fun initialState(name: String) {
        initialState = name
    }

    fun state(name: String, context: StateContext.() -> Unit) =
        StateContext().apply { context() }.build(name).apply { states.add(this) }

    inline fun <reified E : Event> on(
        priority: Int = 0,
        allowRepeats: Boolean = false,
        noinline listener: suspend E.() -> Unit,
    ): EventHandler<E> = eventHandlerOf<E>(priority, allowRepeats, listener).apply {
        globalEvents.add(this)
    }

    fun build(): Graph {
        require(initialState != null) { "Initial state is missing!" }
        val states = this.states.toTypedArray()
        return Graph(states, globalEvents, states.indexOfFirst { it.name == initialState })
    }

    class StateContext {
        private val onEnter = HashSet<suspend StateControl.() -> Unit>()
        private val onUpdate = HashSet<suspend StateControl.() -> Unit>()
        private val onExit = HashSet<suspend CancelableStateControl.() -> Unit>()

        @PublishedApi
        internal val onEvents = HashSet<EventHandler<*>>()

        fun onEnter(action: suspend StateControl.() -> Unit) {
            onEnter.add(action)
        }

        fun onUpdate(action: suspend StateControl.() -> Unit) {
            onUpdate.add(action)
        }

        fun onExit(action: suspend CancelableStateControl.() -> Unit) {
            onExit.add(action)
        }

        inline fun <reified E : Event> on(
            priority: Int = 0,
            allowRepeats: Boolean = false,
            noinline listener: suspend E.() -> Unit,
        ): EventHandler<E> = eventHandlerOf<E>(priority, allowRepeats, listener).apply {
            onEvents.add(this)
        }

        fun build(name: String): State = State(
            name,
            { onEnter.forEach { it() } },
            { onUpdate.forEach { it() } },
            { onExit.forEach { it() } },
            onEvents
        )
    }
}

fun graph(context: GraphContext.() -> Unit) = GraphContext().apply(context).build()