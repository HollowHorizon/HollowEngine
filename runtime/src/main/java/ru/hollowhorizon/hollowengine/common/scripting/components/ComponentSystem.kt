package ru.hollowhorizon.hollowengine.common.scripting.components

import net.minecraft.nbt.CompoundTag
import net.minecraft.server.MinecraftServer
import ru.hollowhorizon.hollowengine.HollowEngine
import ru.hollowhorizon.hollowengine.common.coroutines.runtimeContext
import ru.hollowhorizon.hollowengine.common.events.SubscribeEvent
import ru.hollowhorizon.hollowengine.common.events.tick.TickEvent
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager.fromReadablePath
import ru.hollowhorizon.hollowengine.common.scripting.ScriptingEnvironment

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
                tag.put(name, context.tag)
            }.onFailure {
                HollowEngine.LOGGER.error("Error while saving component '$name'", it)
            }
        }
    }

    fun deserialize(tag: CompoundTag) {
        tag.allKeys.forEach { key ->
            runCatching {
                server.addNode(key, tag.getCompound(key))
            }.onFailure {
                HollowEngine.LOGGER.error("Error while deserializing component '$key'", it)
            }
        }
    }

    fun addNode(node: ComponentScript) {
        components[node.path] = Component(node, node.prepareTickers())
    }

    fun removeNode(path: String) {
        components[path]?.script?.onStop()
        components.remove(path)
    }

    fun dispose() {
        components.keys.forEach(::removeNode)
    }

    private class Component(
        val script: ComponentScript,
        val ticker: Runnable? = null,
    )
}

@SubscribeEvent
fun onServerTick(event: TickEvent.Server) {
    event.server.runtimeContext.components.tick()
}

fun MinecraftServer.addNode(path: String, tag: CompoundTag? = null) {
    val scripting = ScriptingEnvironment.currentOrNull() ?: run {
        HollowEngine.LOGGER.warn("Skipping $path script: Kotlin scripting compiler addon is not installed")
        return
    }

    val script = runCatching {
        scripting.compiler.compile(path.fromReadablePath()).getOrThrow().execute<ComponentScript>(path).getOrThrow()
    }.onFailure {
        HollowEngine.LOGGER.error("Error while loading $path", it)
    }.getOrNull() ?: return

    tag?.let {
        val context = SerializationContext(this, it)
        script.onLoad(context)
    }

    script.onStart()

    runtimeContext.components.addNode(script)
}

fun MinecraftServer.removeNode(path: String) {
    runtimeContext.components.removeNode(path)
}