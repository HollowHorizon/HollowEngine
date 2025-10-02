package ru.hollowhorizon.hollowengine.common.graph

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.IntTag
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.ai.util.DefaultRandomPos
import net.minecraft.world.level.Level
import ru.hollowhorizon.hollowengine.HollowEngine
import ru.hollowhorizon.hollowengine.common.components.annotations.ComponentMeta
import ru.hollowhorizon.hollowengine.common.coroutines.coroutineScope
import ru.hollowhorizon.hollowengine.common.events.Event
import ru.hollowhorizon.hollowengine.common.events.entity.player.PlayerInteractEvent
import ru.hollowhorizon.hollowengine.common.events.server.ServerChatEvent
import ru.hollowhorizon.hollowengine.common.fsm.StateStorage
import ru.hollowhorizon.hollowengine.common.scripting.components.ScriptableComponent
import ru.hollowhorizon.hollowengine.common.scripting.story.functions.npcs.npc
import ru.hollowhorizon.hollowengine.common.scripting.story.functions.npcs.pos
import ru.hollowhorizon.hollowengine.common.scripting.story.functions.player.send
import ru.hollowhorizon.hollowengine.common.utils.currentServer

class Graph(
    coroutineScope: CoroutineScope,
    private val states: Array<State>,
    private val globalEvents: List<EventHandler<*>>,
    private val rememberVariables: List<Variable<*>>,
    initialState: Int,
) : CancelableStateControl {
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
            graphScope,
            states,
            globalEvents,
            rememberVariables,
            states.indexOfFirst { it.name == initialState })
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

@ComponentMeta("hollowengine:story/example_2")
class Example : ScriptableComponent<Level>() {
    init {
        attachGraph {
            val npc by rememberEntity {
                npc(pos(47, -57, -12))
            }

            initialState("Move to player")

            state("Move to player") {
                onEnter {
                    while(true) {
                        delay(50)
                        val player = owner.getNearestPlayer(npc, 100.0) ?: continue
                        npc.navigation.moveTo(player, 1.0)
                    }
                }

                on<ServerChatEvent> {
                    val message = it.message.string.lowercase()
                    when {
                        "уйди" in message -> transition("Move from player")
                        "стой" in message -> transition("Stay")
                    }
                }
            }

            state("Move from player") {
                onEnter {
                    while(true) {
                        delay(50)
                        val player = owner.getNearestPlayer(npc, 100.0) ?: continue
                        val avoidPos = DefaultRandomPos.getPosAway(npc, 16, 7, player.position()) ?: continue
                        npc.navigation.moveTo(avoidPos.x, avoidPos.y, avoidPos.z, 1.0)
                    }
                }

                on<ServerChatEvent> {
                    val message = it.message.string.lowercase()
                    when {
                        "за мной" in message -> transition("Move to player")
                        "стой" in message -> transition("Stay")
                    }
                }
            }

            state("Stay") {
                onEnter {
                    npc.navigation.stop()
                }

                on<ServerChatEvent> {
                    val message = it.message.string.lowercase()
                    when {
                        "уйди" in message -> transition("Move from player")
                        "за мной" in message -> transition("Move to player")
                    }
                }
            }

        }
    }
}

fun ScriptableComponent<Level>.attachGraph(context: GraphContext.() -> Unit) {
    val graph = currentServer.coroutineScope.graph(context)

    onAttach {
        if (owner.isClientSide) return@onAttach
        if (!graph.isStarted) graph.start()
    }
    onDetach {
        if (owner.isClientSide) return@onDetach
        graph.stop()
    }
    onUpdate {
        if (owner.isClientSide) return@onUpdate
        graph.update()
    }
    onSave {
        if (owner.isClientSide) return@onSave
        put("graph", graph.serialize())
    }
    onLoad {
        if (owner.isClientSide) return@onLoad
        graph.start(getCompound("graph"))
    }
}