package ru.hollowhorizon.hollowengine.common.scripting.components

import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.server.packs.resources.ResourceManagerReloadListener
import ru.hollowhorizon.hollowengine.HollowEngine
import ru.hollowhorizon.hollowengine.common.components.Component
import ru.hollowhorizon.hollowengine.common.components.registry.ComponentEntry
import ru.hollowhorizon.hollowengine.common.components.registry.ComponentRegistry
import ru.hollowhorizon.hollowengine.common.events.Event
import ru.hollowhorizon.hollowengine.common.events.SubscribeEvent
import ru.hollowhorizon.hollowengine.common.events.post
import ru.hollowhorizon.hollowengine.common.events.registry.RegisterReloadListenersEvent
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager.toReadablePath
import ru.hollowhorizon.hollowengine.common.scripting.ScriptingEnvironment
import ru.hollowhorizon.hollowengine.common.scripting.compiling.CompiledScript
import ru.hollowhorizon.hollowengine.common.utils.rl
import java.lang.reflect.ParameterizedType
import kotlin.reflect.KClass

@SubscribeEvent
fun onRegisterManagers(event: RegisterReloadListenersEvent.Server) {
    event.register(ComponentReloadManager)
}

object ComponentReloadManager : ResourceManagerReloadListener {
    override fun onResourceManagerReload(resourceManager: ResourceManager) {
        if (!HollowEngine.compilerLoader.isLoaded) return

        val components = DirectoryManager.HOLLOW_ENGINE.resolve("scripts").toFile()
            .walk()
            .filter { it.isFile && it.name.endsWith(".entity-component.kts") }
            .map { it.toReadablePath() to ScriptingEnvironment.INSTANCE.compiler.compile(it) }
            .filter { (_, it) ->
                val result = it.isSuccess
                if(!result) it.exceptionOrNull()?.stackTraceToString()?.let { HollowEngine.LOGGER.error(it) }
                result
            }
            .map { (path, it) -> path to it.getOrThrow().base }

        ComponentRegistry.apply {
            try {
                unfreeze()
                unbake()

                components.forEach { (path, script) ->
                    unregister("hollowengine:$path".rl)

                    register("hollowengine:$path".rl) {
                        ComponentEntry(getComponentType(script)) { script.execute<Component<Any>>(it).getOrThrow() }
                    }
                }
            } finally {
                bake()
                freeze()
            }
        }

        ScriptComponentsReloadedEvent().post()
    }

    private fun getComponentType(script: CompiledScript): KClass<Any> {
        return (((script.type.java.genericSuperclass as Class<*>).genericSuperclass as ParameterizedType).actualTypeArguments[0] as Class<*>).kotlin as KClass<Any>
    }
}

class ScriptComponentsReloadedEvent: Event