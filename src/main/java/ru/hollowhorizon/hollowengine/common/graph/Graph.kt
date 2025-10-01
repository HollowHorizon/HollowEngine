package ru.hollowhorizon.hollowengine.common.graph

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.IntTag
import ru.hollowhorizon.hollowengine.HollowEngine
import ru.hollowhorizon.hollowengine.common.events.Event

class Graph(
    private val states: Array<State>,
    private val globalEvents: Set<EventHandler<*>>,
    private val rememberVariables: HashSet<Variable<*>>,
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

    private var startJob: Job? = null

    fun start(scope: CoroutineScope, tag: CompoundTag = CompoundTag()) {
        if (startJob?.isActive == true) error("Graph already started!")
        startJob = scope.launch {
            deserialize(tag)
            globalEvents.forEach { event -> event.subscribe() }
            currentState.status = Status.ENTER
        }
    }

    suspend fun deserialize(tag: CompoundTag) {
        currentIndex = (tag.get("index") as? IntTag)?.asInt ?: currentIndex

        val variables = tag.getCompound("variables")

        rememberVariables.forEach { variable ->
            variable.init(variables.get(variable.name()))
        }
    }

    fun stop(): CompoundTag {
        globalEvents.forEach { event -> event.unsubscribe() }
        currentState.cancel()

        return serialize()
    }

    fun serialize(): CompoundTag = CompoundTag().apply {
        putInt("index", currentIndex)
        put("variables", CompoundTag().apply {
            rememberVariables.forEach { variable ->
                variable.serialize()?.let { put(variable.name(), it) }
            }
        })
    }

    fun update(scope: CoroutineScope) {
        if (startJob?.isActive == true) return

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
                                if(status == Status.ENTER) status = Status.UPDATE
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
        startJob?.join()
        currentState.await()
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

@DslMarker
annotation class GraphDSL

@GraphDSL
class GraphContext {
    @PublishedApi
    internal var eventScope: CoroutineScope? = null
    private val states = HashSet<State>()

    @PublishedApi
    internal val globalEvents = HashSet<EventHandler<*>>()

    @PublishedApi
    internal var rememberVariables = HashSet<Variable<*>>()
    private var initialState: String? = null


    fun initialState(name: String) {
        initialState = name
    }

    fun eventScope(scope: CoroutineScope) {
        this.eventScope = scope
    }

    fun state(name: String, context: StateContext.() -> Unit) =
        StateContext(eventScope).apply { context() }.build(name).apply { states.add(this) }

    inline fun <reified T : Any> remember(name: String? = null, noinline default: suspend () -> T): Variable<T> {
        return Variable(name, default, T::class.java).apply {
            rememberVariables += this
        }
    }

    inline fun <reified E : Event> on(
        priority: Int = 0,
        allowRepeats: Boolean = false,
        noinline listener: suspend E.() -> Unit,
    ): EventHandler<E> = eventHandlerOf<E>(eventScope, priority, allowRepeats, listener).apply {
        globalEvents.add(this)
    }

    fun build(): Graph {
        require(initialState != null) { "Initial state is missing!" }
        val states = this.states.toTypedArray()
        return Graph(states, globalEvents, rememberVariables, states.indexOfFirst { it.name == initialState })
    }

    @GraphDSL
    class StateContext(val eventScope: CoroutineScope?) {
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
        ): EventHandler<E> = eventHandlerOf<E>(eventScope, priority, allowRepeats, listener).apply {
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

@GraphDSL
fun graph(context: GraphContext.() -> Unit) = GraphContext().apply(context).build()