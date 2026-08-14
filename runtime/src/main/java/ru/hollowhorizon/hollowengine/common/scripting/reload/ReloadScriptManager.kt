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
import ru.hollowhorizon.hollowengine.common.scripting.source.ScriptId
import ru.hollowhorizon.hollowengine.common.scripting.source.ScriptRegistry
import ru.hollowhorizon.hollowengine.common.utils.UnsafeTools
import java.lang.invoke.MethodHandles
import java.lang.reflect.Method
import java.lang.reflect.Modifier
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

        events.forEach { handle ->
            runCatching { handle.unsubscribe() }
                .onFailure { error ->
                    HollowEngine.LOGGER.error("Failed to unsubscribe reload script event handler", error)
                }
        }
        events = emptyList()

        events = ScriptRegistry.list(".$RELOAD_SCRIPT_EXTENSION")
            .mapNotNull { id ->
                runCatching {
                    ScriptLoader.execute<ReloadScript>(id) {
                        constructorArgs(context)
                    }.getOrThrow()
                }.onFailure { error ->
                    HollowEngine.LOGGER.error(
                        "Failed to execute reload script: {}",
                        ScriptRegistry.display(id),
                        error,
                    )
                }.getOrNull()?.let { script ->
                    LoadedReloadScript(id, script)
                }
            }
            .makeHandles()
    }

    private fun List<LoadedReloadScript>.makeHandles(): List<EventHandle> {
        return flatMap { loadedScript ->
            val script = loadedScript.script
            val scriptClass = script.javaClass
            val classLoader = Thread.currentThread().contextClassLoader
            runCatching {
                try {
                    val lookup = MethodHandles.privateLookupIn(scriptClass, UnsafeTools.lookup)

                    Thread.currentThread().contextClassLoader = script.javaClass.classLoader
                    scriptClass.declaredMethods
                        .filter { method -> method.isAnnotationPresent(SubscribeEvent::class.java) }
                        .mapNotNull { method ->
                            loadedScript.createHandle(lookup, method)
                        }
                } finally {
                    Thread.currentThread().contextClassLoader = classLoader
                }
            }.onFailure { error ->
                HollowEngine.LOGGER.error(
                    "Failed to inspect reload script '{}' for @SubscribeEvent handlers",
                    ScriptRegistry.display(loadedScript.id),
                    error,
                )
            }.getOrDefault(emptyList())
        }
    }

    private fun LoadedReloadScript.createHandle(
        lookup: MethodHandles.Lookup,
        method: Method,
    ): EventHandle? {
        if (!hasValidEventSignature(method)) return null

        return runCatching {
            @Suppress("UNCHECKED_CAST")
            val handler = EventHandler.get(method.parameterTypes.single().kotlin as KClass<Event>)
            val listener = lookup.createEventListener(method, script).safe(scriptId = id, method = method)
            EventHandle.of(handler, listener).also(EventHandle::subscribe)
        }.onFailure { error ->
            HollowEngine.LOGGER.error(
                "Failed to register @SubscribeEvent handler '{}#{}' in reload script '{}'",
                script.javaClass.name,
                method.name,
                ScriptRegistry.display(id),
                error,
            )
        }.getOrNull()
    }

    private fun LoadedReloadScript.hasValidEventSignature(method: Method): Boolean {
        val problem = when {
            Modifier.isStatic(method.modifiers) -> "it must be an instance method"
            method.parameterCount != 1 -> "it declares ${method.parameterCount} parameters instead of exactly one"
            !Event::class.java.isAssignableFrom(method.parameterTypes.single()) ->
                "its parameter '${method.parameterTypes.single().name}' does not implement ${Event::class.java.name}"
            method.returnType != Void.TYPE -> "it returns '${method.returnType.name}' instead of Unit"
            else -> return true
        }

        HollowEngine.LOGGER.error(
            "Invalid @SubscribeEvent signature in reload script '{}', method '{}': {}. " +
                "Expected an instance method with exactly one ${Event::class.java.simpleName} parameter and a Unit return type. " +
                "Actual signature: {}",
            ScriptRegistry.display(id),
            method.name,
            problem,
            method.toGenericString(),
        )
        return false
    }

    private fun EventListener<Event>.safe(scriptId: ScriptId, method: Method): EventListener<Event> {
        val delegate = this
        return object : EventListener<Event> {
            override val priority: Int = delegate.priority

            override fun invoke(event: Event) {
                runCatching { delegate(event) }
                    .onFailure { error ->
                        HollowEngine.LOGGER.error(
                            "Error in reload script event handler '{}#{}' while handling {}",
                            ScriptRegistry.display(scriptId),
                            method.name,
                            event.javaClass.name,
                            error,
                        )
                    }
            }
        }
    }

    private data class LoadedReloadScript(val id: ScriptId, val script: ReloadScript)

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
