package ru.hollowhorizon.hollowengine.common.scripting.nodes

import kotlinx.coroutines.*
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.MinecraftServer
import ru.hollowhorizon.hollowengine.HollowEngine
import ru.hollowhorizon.hollowengine.common.coroutines.runtimeContext
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager.fromReadablePath
import ru.hollowhorizon.hollowengine.common.scripting.ScriptingEnvironment
import ru.hollowhorizon.hollowengine.common.scripting.state.StateContext
import ru.hollowhorizon.hollowengine.common.scripting.state.StateExecutor
import kotlin.reflect.KClass
import kotlin.script.experimental.api.constructorArgs
import kotlin.script.experimental.api.implicitReceivers

/**
 * Holds the server-bound nodes for a single [MinecraftServer]. Ticking is no longer driven from here,
 * nodes subscribe to [ru.hollowhorizon.hollowengine.common.events.tick.TickEvent.Server] through
 * `onUpdate`, so this manager only owns lifecycle, persistence and lookup.
 */
class NodeManager(val server: MinecraftServer) {
    private val nodes = mutableMapOf<String, RunningNode>()

    fun serialize(tag: CompoundTag) {
        nodes.forEach { (name, entry) ->
            runCatching {
                val context = SerializationContext(server, CompoundTag())
                entry.script.onSaveHandlers.forEach { it(context) }
                val nodeTag = CompoundTag()
                nodeTag.put("extras", context.tag)
                entry.context?.let { nodeTag.put("states", it.serialize()) }
                tag.put(name, nodeTag)
            }.onFailure {
                HollowEngine.LOGGER.error("Error while saving node '$name'", it)
            }
        }
    }

    fun deserialize(tag: CompoundTag) {
        tag.allKeys.forEach { key ->
            runCatching {
                val nodeTag = tag.getCompound(key)
                val extras = nodeTag.getCompound("extras")
                val context = (nodeTag.get("states") as? CompoundTag)
                    ?.let { StateContext.deserialize(it) }
                server.addNode(key, extras, context)
            }.onFailure {
                HollowEngine.LOGGER.error("Error while deserializing node '$key'", it)
            }
        }
    }

    internal fun register(script: NodeScript, executor: StateExecutor, context: StateContext?) {
        nodes[script.path] = RunningNode(script, executor, context)
        context?.let { executor.start(it) }
    }

    fun removeNode(path: String) {
        nodes.remove(path)?.script?.coroutineContext?.job?.cancel()
    }

    fun dispose() {
        nodes.keys.toList().forEach(::removeNode)
    }

    fun contains(path: String): Boolean = nodes.containsKey(path)

    fun paths(): Set<String> = nodes.keys.toSet()
}

internal class RunningNode(
    val script: NodeScript,
    val executor: StateExecutor,
    val context: StateContext?,
)

/**
 * Compiles [path], binds it to a fresh child scope of [host]'s scope, runs the script body (which
 * registers its handlers), then replays `onLoad` -> `onStart` -> `@State` states.
 *
 * The bound [NodeScript] is returned so the caller can register it with the owning manager; `null` is
 * returned when compilation is unavailable or fails.
 */
internal fun buildNode(
    host: NodeHost,
    parentScope: CoroutineScope,
    path: String,
    tag: CompoundTag?,
    receivers: List<Any>,
): Pair<NodeScript, StateExecutor>? {
    val scripting = ScriptingEnvironment.currentOrNull() ?: run {
        HollowEngine.LOGGER.warn("Skipping $path node: Kotlin scripting compiler addon is not installed")
        return null
    }

    val nodeScope = CoroutineScope(parentScope.coroutineContext + SupervisorJob(parentScope.coroutineContext.job))
    val binding = NodeBinding(host, nodeScope)

    val script = runCatching {
        val compiled = scripting.compiler.compile(path.fromReadablePath()).getOrThrow()
        receiverMismatch(compiled.type, host, receivers)?.let { error(it) }
        compiled.execute<NodeScript> {
            constructorArgs(path, binding)
            implicitReceivers(*receivers.toTypedArray())
        }.getOrThrow()
    }.onFailure {
        HollowEngine.LOGGER.error("Error while loading $path", it)
        nodeScope.cancel()
    }.getOrNull() ?: return null

    val executor = StateExecutor(nodeScope)
    script.prepareExecutor(executor)

    tag?.let { extras ->
        val context = SerializationContext(host.server, extras)
        script.onLoadHandlers.forEach { it(context) }
    }

    script.onStartHandlers.forEach { start -> nodeScope.launch { start() } }

    return script to executor
}

/**
 * Explains a receiver count that will not fit, before the evaluator fails on it with a bare
 * `WrongMethodTypeException`.
 *
 * A script's compiled constructor takes its path, its binding, and one argument per implicit receiver.
 * `@file:Attach` adds a receiver, so an attached script cannot be started as a plain server node and the
 * other way round. That is easy to do by accident, and the raw method-handle error says nothing about why.
 */
private fun receiverMismatch(type: KClass<*>, host: NodeHost, receivers: List<Any>): String? {
    val expected = type.java.constructors.minOfOrNull { it.parameterCount } ?: return null
    val given = receivers.size + FixedConstructorArgs
    if (expected == given) return null

    val wantsMore = expected > given
    val hint = when {
        wantsMore && host is NodeHost.Server ->
            "it declares @file:Attach, so attach it to an entity instead of starting it as a server node"

        !wantsMore && host is NodeHost.OfEntity ->
            "it has no @file:Attach, so start it as a server node instead of attaching it to an entity"

        wantsMore -> "it expects more implicit receivers than this host provides"
        else -> "it expects fewer implicit receivers than this host provides"
    }
    return "Cannot run node script as ${host.describe()}: $hint " +
            "(script takes $expected constructor arguments, this host supplies $given)"
}

private fun NodeHost.describe(): String = when (this) {
    is NodeHost.Server -> "a server node"
    is NodeHost.OfEntity -> "a node attached to ${entity.type.description.string}"
}

/** The path and the binding, which every node script takes before its implicit receivers. */
private const val FixedConstructorArgs = 2

fun MinecraftServer.addNode(path: String, tag: CompoundTag? = null, context: StateContext? = null) {
    val (script, executor) = buildNode(
        host = NodeHost.Server(this),
        parentScope = runtimeContext.scope,
        path = path,
        tag = tag,
        receivers = listOf(this),
    ) ?: return

    runtimeContext.nodes.register(script, executor, context)
}

fun MinecraftServer.removeNode(path: String) {
    runtimeContext.nodes.removeNode(path)
}
