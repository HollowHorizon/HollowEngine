package ru.hollowhorizon.hollowengine.common.scripting.reload

import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.server.packs.resources.ResourceManagerReloadListener
import ru.hollowhorizon.hollowengine.HollowEngine
import ru.hollowhorizon.hollowengine.api.ReloadListener
import ru.hollowhorizon.hollowengine.common.compat.util.currentRecipeManagerOrNull
import ru.hollowhorizon.hollowengine.common.events.Event
import ru.hollowhorizon.hollowengine.common.events.EventListener
import ru.hollowhorizon.hollowengine.common.events.SubscribeEvent
import ru.hollowhorizon.hollowengine.common.events.createEventListener
import ru.hollowhorizon.hollowengine.common.events.factory.EventHandler
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager.toReadablePath
import ru.hollowhorizon.hollowengine.common.scripting.ScriptingEnvironment
import ru.hollowhorizon.hollowengine.common.utils.UnsafeTools
import java.lang.invoke.MethodHandles
import kotlin.jvm.java
import kotlin.reflect.KClass

@ReloadListener
object ReloadScriptManager : ResourceManagerReloadListener {
    private var events: List<EventHandle> = emptyList()



    override fun onResourceManagerReload(resourceManager: ResourceManager) {
        val recipeManager = currentRecipeManagerOrNull() ?: run {
            HollowEngine.LOGGER.warn("Skipping reload scripts: RecipeManager is not initialized yet")
            return
        }

        val scriptsDir = DirectoryManager.HOLLOW_ENGINE.resolve("scripts").toFile()
        if (!scriptsDir.exists()) return

        val context =
            ReloadScriptContext(server = null, resourceManager = resourceManager, recipeManager = recipeManager)
        val scripting = ScriptingEnvironment.currentOrNull() ?: run {
            HollowEngine.LOGGER.warn("Skipping reload scripts: Kotlin scripting compiler addon is not installed")
            return
        }

        events.forEach { handler ->
            handler.unsubscribe()
        }
        events = scriptsDir.walk()
            .filter { it.isFile && it.name.endsWith(".reload.kts") }
            .mapNotNull { file ->
                val path = file.toReadablePath()
                runCatching {
                    scripting.compiler.compile(file).getOrThrow().execute<ReloadScript>(context).getOrThrow()
                }.onFailure { error ->
                    HollowEngine.LOGGER.error("Failed to execute reload script: {}", path, error)
                }.getOrNull()
            }
            .makeHandles()
    }

    private fun Sequence<ReloadScript>.makeHandles(): List<EventHandle> {
        return this.flatMap { script ->
            val scriptClass = script.javaClass
            val classLoader = Thread.currentThread().contextClassLoader
            try {
                val lookup = MethodHandles.privateLookupIn(scriptClass, UnsafeTools.lookup)

                Thread.currentThread().contextClassLoader = script.javaClass.classLoader
                scriptClass.declaredMethods
                    .filter {
                        val hasAnnotation = it.isAnnotationPresent(SubscribeEvent::class.java)
                        if (!hasAnnotation) return@filter false
                        val parameterType = it.parameters.singleOrNull()?.type ?: return@filter false
                        Event::class.java.isAssignableFrom(parameterType)
                    }
                    .map { method ->
                        val handler = EventHandler.get(method.parameterTypes[0].kotlin as KClass<Event>)
                        val listener = lookup.createEventListener(method, script)
                        val handle = EventHandle.of(handler, listener)
                        handle.subscribe()
                        handle
                    }
            } finally {
                Thread.currentThread().contextClassLoader = classLoader
            }
        }.toList()
    }

    private interface EventHandle {
        fun subscribe()
        fun unsubscribe()

        companion object {
            fun of(handler: EventHandler<Event>, listener: EventListener<Event>) = object : EventHandle {
                override fun subscribe() {
                    handler.register(listener)
                }

                override fun unsubscribe() {
                    handler.unregister(listener)
                }
            }
        }
    }
}
