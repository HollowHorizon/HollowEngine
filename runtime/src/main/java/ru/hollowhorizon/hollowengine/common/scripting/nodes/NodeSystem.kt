package ru.hollowhorizon.hollowengine.common.scripting.nodes

import net.minecraft.nbt.CompoundTag
import net.minecraft.server.MinecraftServer
import ru.hollowhorizon.hollowengine.HollowEngine
import ru.hollowhorizon.hollowengine.common.coroutines.coroutineScope
import ru.hollowhorizon.hollowengine.common.coroutines.runtimeContext
import ru.hollowhorizon.hollowengine.common.events.SubscribeEvent
import ru.hollowhorizon.hollowengine.common.events.tick.TickEvent
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager.fromReadablePath
import ru.hollowhorizon.hollowengine.common.scripting.ScriptingEnvironment
import ru.hollowhorizon.hollowengine.common.scripting.state.StateContext
import ru.hollowhorizon.hollowengine.common.scripting.state.StateExecutor
import kotlin.script.experimental.api.constructorArgs
import kotlin.script.experimental.api.implicitReceivers

class ComponentSystem(val server: MinecraftServer) {
    private val components = mutableMapOf<String, Component>()


    fun tick() {
        components.values.forEach {
            it.ticker?.run()
        }
    }

    fun serialize(tag: CompoundTag) {
        components.forEach { (name, component) ->
            runCatching {
                val context = SerializationContext(server, CompoundTag())
                component.script.onSave(context)
                val componentTag = CompoundTag()
                componentTag.put("extras", context.tag)
                component.context?.let {
                    componentTag.put("states", it.serialize())
                }
                tag.put(name, componentTag)
            }.onFailure {
                HollowEngine.LOGGER.error("Error while saving component '$name'", it)
            }
        }
    }

    fun deserialize(tag: CompoundTag) {
        tag.allKeys.forEach { key ->
            runCatching {
                val componentTag = tag.getCompound(key)
                val extras = componentTag.getCompound("extras")
                val context = (componentTag.get("states") as? CompoundTag)
                    ?.let { StateContext.deserialize(it) }
                server.addNode(key, extras, context)
            }.onFailure {
                HollowEngine.LOGGER.error("Error while deserializing component '$key'", it)
            }
        }
    }

    fun addNode(node: NodeScript, context: StateContext?) {
        val executor = StateExecutor(server.coroutineScope)
        node.prepareExecutor(executor)
        components[node.path] = Component(node, node.prepareTickers(), executor, context)
        context?.let { executor.start(it) }
    }

    fun removeNode(path: String) {
        components[path]?.script?.onStop()
        components.remove(path)
    }

    fun dispose() {
        components.keys.forEach(::removeNode)
    }

    private class Component(
        val script: NodeScript,
        val ticker: Runnable? = null,
        val executor: StateExecutor,
        val context: StateContext?,
    )
}

@SubscribeEvent
fun onServerTick(event: TickEvent.Server) {
    event.server.runtimeContext.components.tick()
}

fun MinecraftServer.addNode(path: String, tag: CompoundTag? = null, context: StateContext? = null) {
    val scripting = ScriptingEnvironment.currentOrNull() ?: run {
        HollowEngine.LOGGER.warn("Skipping $path script: Kotlin scripting compiler addon is not installed")
        return
    }

    val script = runCatching {
        scripting.compiler.compile(path.fromReadablePath()).getOrThrow().execute<NodeScript> {
            constructorArgs(path)
            implicitReceivers(this@addNode)
        }.getOrThrow()
    }.onFailure {
        HollowEngine.LOGGER.error("Error while loading $path", it)
    }.getOrNull() ?: return

    tag?.let {
        val context = SerializationContext(this, it)
        script.onLoad(context)
    }

    script.onStart()

    runtimeContext.components.addNode(script, context)
}

fun MinecraftServer.removeNode(path: String) {
    runtimeContext.components.removeNode(path)
}