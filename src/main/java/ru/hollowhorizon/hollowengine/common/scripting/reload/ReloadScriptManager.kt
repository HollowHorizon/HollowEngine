package ru.hollowhorizon.hollowengine.common.scripting.reload

import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.server.packs.resources.ResourceManagerReloadListener
import ru.hollowhorizon.hollowengine.HollowEngine
import ru.hollowhorizon.hollowengine.common.compat.util.currentRecipeManagerOrNull
import ru.hollowhorizon.hollowengine.common.events.SubscribeEvent
import ru.hollowhorizon.hollowengine.common.events.registry.RegisterReloadListenersEvent
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager.toReadablePath
import ru.hollowhorizon.hollowengine.common.scripting.ScriptingEnvironment

@SubscribeEvent
fun registerReloadScriptManager(event: RegisterReloadListenersEvent.Server) {
    event.register(ReloadScriptManager)
}

object ReloadScriptManager : ResourceManagerReloadListener {
    override fun onResourceManagerReload(resourceManager: ResourceManager) {
        if (!HollowEngine.compilerLoader.isLoaded) return
        val recipeManager = currentRecipeManagerOrNull() ?: run {
            HollowEngine.LOGGER.warn("Skipping reload scripts: RecipeManager is not initialized yet")
            return
        }

        val scriptsDir = DirectoryManager.HOLLOW_ENGINE.resolve("scripts").toFile()
        if (!scriptsDir.exists()) return

        val context = ReloadScriptContext(server = null, resourceManager = resourceManager, recipeManager = recipeManager)
        scriptsDir.walk()
            .filter { it.isFile && it.name.endsWith(".reload.kts") }
            .forEach { file ->
                val path = file.toReadablePath()
                runCatching {
                    ScriptingEnvironment.INSTANCE.compiler.compile(file).getOrThrow().execute<Any>(context).getOrThrow()
                }.onFailure { error ->
                    HollowEngine.LOGGER.error("Failed to execute reload script: {}", path, error)
                }
            }

        context.flushRecipes()
    }
}
