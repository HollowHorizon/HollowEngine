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
import ru.hollowhorizon.hollowengine.common.scripting.RELOAD_SCRIPT_EXTENSION
import ru.hollowhorizon.hollowengine.common.scripting.ScriptLoader
import ru.hollowhorizon.hollowengine.common.scripting.source.ScriptRegistry
import ru.hollowhorizon.hollowengine.common.utils.UnsafeTools
import java.lang.invoke.MethodHandles
import kotlin.reflect.KClass
import kotlin.script.experimental.api.constructorArgs

@ReloadListener
object ReloadScriptManager : ResourceManagerReloadListener {
    private var events: List<EventHandle> = emptyList()
    private var lastContext: ReloadScriptContext? = null

    override fun onResourceManagerReload(resourceManager: ResourceManager) {
        val recipeManager = currentRecipeManagerOrNull() ?: run {
            HollowEngine.LOGGER.warn("Skipping reload scripts: RecipeManager is not initialized yet")
            return
        }

        run(ReloadScriptContext(server = null, resourceManager = resourceManager, recipeManager = recipeManager))
    }

    /**
     * Runs the reload scripts again with the context of the last resource reload. Used when a namespace
     * appears or disappears outside of a resource reload, e.g. an addon being enabled.
     */
    fun rerun() {
        val context = lastContext ?: return
        run(context)
    }

    private fun run(context: ReloadScriptContext) {
        lastContext = context

        events.forEach { handler ->
            handler.unsubscribe()
        }
        events = ScriptRegistry.list(".$RELOAD_SCRIPT_EXTENSION")
            .mapNotNull { id ->
                ScriptLoader.execute<ReloadScript>(id) {
                    constructorArgs(context)
                }.onFailure { error ->
                    HollowEngine.LOGGER.error(
                        "Failed to execute reload script: {}",
                        ScriptRegistry.display(id),
                        error,
                    )
                }.getOrNull()
            }
            .makeHandles()
    }

    private fun List<ReloadScript>.makeHandles(): List<EventHandle> {
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
