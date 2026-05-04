package ru.hollowhorizon.hollowengine.common.scripting.components

import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.server.packs.resources.ResourceManagerReloadListener
import ru.hollowhorizon.hollowengine.HollowEngine
import ru.hollowhorizon.hollowengine.common.events.registry.RegisterReloadListenersEvent
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager.toReadablePath
import ru.hollowhorizon.hollowengine.common.scripting.ScriptingEnvironment
import ru.hollowhorizon.hollowengine.common.scripting.compiling.CompiledScript
import java.lang.reflect.ParameterizedType
import kotlin.reflect.KClass

// @SubscribeEvent
fun onRegisterManagers(event: RegisterReloadListenersEvent.Server) {
    event.register(ComponentReloadManager)
}

object ComponentReloadManager : ResourceManagerReloadListener {
    override fun onResourceManagerReload(resourceManager: ResourceManager) {

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

    }

    private fun getComponentType(script: CompiledScript): KClass<Any> {
        return (((script.type.java.genericSuperclass as Class<*>).genericSuperclass as ParameterizedType).actualTypeArguments[0] as Class<*>).kotlin as KClass<Any>
    }
}