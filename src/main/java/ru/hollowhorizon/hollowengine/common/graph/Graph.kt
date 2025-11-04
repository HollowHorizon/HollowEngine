package ru.hollowhorizon.hollowengine.common.graph

import kotlinx.coroutines.*
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.IntTag
import ru.hollowhorizon.hollowengine.HollowEngine
import ru.hollowhorizon.hollowengine.common.components.binding.Bindable
import ru.hollowhorizon.hollowengine.common.events.Event
import ru.hollowhorizon.hollowengine.common.fsm.StateStorage

class Graph(
    coroutineScope: CoroutineScope,
    private val states: Array<State>,
    private val globalEvents: List<EventHandler<*>>,
    private val rememberVariables: List<Variable<*>>,
    initialState: Int,
) : CancelableStateControl, Bindable {
    init {
        require(initialState in states.indices) { "Initial state index is out of bounds!" }

        globalEvents.forEach { it.graph = this }
        states.forEach { it.applyGraph(this) }
    }

    private val extras = CompoundTag()
    val graphScope = coroutineScope + StateStorage(extras)

    var exitCanceled = false
    var currentIndex = initialState
        private set
    val currentState: State get() = states[currentIndex]

    var nextState: Int? = null

    private var startJob: Job? = null

    fun start(tag: CompoundTag = CompoundTag()) {
        if (startJob?.isActive == true) error("Graph already started!")
        startJob = graphScope.launch {
            deserialize(tag)
            globalEvents.forEach { event -> event.subscribe() }
            currentState.status = Status.ENTER
        }
    }

    fun stop(): CompoundTag {
        globalEvents.forEach { event -> event.unsubscribe() }
        currentState.unsubscribe()
        currentState.cancel()

        return serialize()
    }

    suspend fun deserialize(tag: CompoundTag) {
        currentIndex = (tag.get("index") as? IntTag)?.asInt ?: currentIndex

        val variables = tag.getCompound("variables")

        rememberVariables.forEach { variable ->
            variable.deserialize(variables.get(variable.name()))
        }

        tag.getCompound("extras").apply {
            allKeys.forEach {
                val value = get(it) ?: return@forEach
                extras.put(it, value)
            }
        }
    }

    fun serialize(): CompoundTag = CompoundTag().apply {
        putInt("index", currentIndex)
        if (rememberVariables.isNotEmpty()) put("variables", CompoundTag().apply {
            rememberVariables.forEach { variable ->
                variable.serialize()?.let { put(variable.name(), it) }
            }
        })
        if (!extras.isEmpty) put("extras", extras)
    }

    fun update() {
        if (startJob?.isActive == true) return

        currentState.apply {
            when (status) {
                Status.ENTER -> {
                    if (enterJob?.isActive != true) {
                        subscribe()
                        enterJob = graphScope.launch {
                            try {
                                onEnter()
                            } catch (_: CancellationException) {
                                // Отмены корутин это не ошибки в данном случае
                            } catch (e: Exception) {
                                HollowEngine.LOGGER.error("Error in state $name on enter: ", e)
                            } finally {
                                if (status == Status.ENTER) status = Status.UPDATE
                            }
                        }
                    }
                }

                Status.UPDATE -> {
                    if (updateJob?.isActive != true) {
                        updateJob = graphScope.launch {
                            onUpdate()
                        }
                    }
                }

                Status.EXIT -> {
                    if (exitJob?.isActive != true) {
                        exitJob = graphScope.launch {
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


    override fun onAttach() {
        if (!isStarted) start()
    }

    override fun onDetach() {
        stop()
    }

    override fun onSave(tag: CompoundTag) {
        tag.put("graph", serialize())
    }

    override fun onLoad(tag: CompoundTag) {
        start(tag.getCompound("graph"))
    }

    override fun onUpdate() {
        update()
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
    val isStarted get() = startJob != null

}

@DslMarker
annotation class GraphDSL

@GraphDSL
class GraphContext(@PublishedApi internal val graphScope: CoroutineScope) {
    private val states = mutableListOf<State>()

    @PublishedApi
    internal val globalEvents = mutableListOf<EventHandler<*>>()

    @PublishedApi
    internal val rememberVariables = mutableListOf<Variable<*>>()
    private var initialState: String? = null


    fun initialState(name: String) {
        initialState = name
    }

    fun state(name: String, context: StateContext.() -> Unit) =
        StateContext(graphScope).apply { context() }.build(name).apply { states.add(this) }

    inline fun <reified T : Any> remember(name: String? = null, noinline default: suspend () -> T): Variable<T> {
        return GraphVariable(name, default, T::class.java).apply {
            rememberVariables += this
        }
    }

    inline fun <reified E : Event> on(
        priority: Int = 0,
        allowRepeats: Boolean = false,
        noinline listener: StateControl.(E) -> Unit,
    ): EventHandler<E> = eventHandlerOf<E>(graphScope, priority, allowRepeats, listener).apply {
        globalEvents.add(this)
    }

    fun build(): Graph {
        require(initialState != null) { "Initial state is missing!" }
        val states = this.states.toTypedArray()
        return Graph(
            graphScope, states, globalEvents, rememberVariables, states.indexOfFirst { it.name == initialState })
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
            noinline listener: StateControl.(E) -> Unit,
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
fun CoroutineScope.graph(context: GraphContext.() -> Unit) = GraphContext(this).apply(context).build()