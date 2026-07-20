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
        scripting.compiler.compile(path.fromReadablePath()).getOrThrow().execute<NodeScript> {
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
