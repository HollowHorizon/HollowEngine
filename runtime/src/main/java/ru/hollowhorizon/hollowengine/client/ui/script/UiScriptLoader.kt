package ru.hollowhorizon.hollowengine.client.ui.script

import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.server.packs.resources.ResourceManagerReloadListener
import ru.hollowhorizon.hollowengine.HollowEngine
import ru.hollowhorizon.hollowengine.api.ReloadListener
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager.toReadablePath
import ru.hollowhorizon.hollowengine.common.scripting.ScriptingEnvironment
import ru.hollowhorizon.hollowengine.common.scripting.UI_SCRIPT_EXTENSION
import ru.hollowhorizon.hollowengine.common.scripting.ui.UiScript
import ru.hollowhorizon.hollowengine.common.ui.UiDefinitionRegistry
import ru.hollowhorizon.hollowengine.common.utils.Side

/**
 * Compiles and runs the `.ui.kts` scripts under `hollowengine/scripts`. Running a UI script only
 * registers declarations, nothing is composed until a screen is opened or an overlay is shown, so
 * a reload is cheap and free of side effects.
 */
@ReloadListener(Side.CLIENT)
object UiScriptLoader : ResourceManagerReloadListener {
    override fun onResourceManagerReload(resourceManager: ResourceManager) = reload()

    fun reload() {
        val scripting = ScriptingEnvironment.currentOrNull() ?: run {
            HollowEngine.LOGGER.warn("Skipping UI scripts: Kotlin scripting compiler addon is not installed")
            return
        }

        UiDefinitionRegistry.clear()
        val scriptsDir = DirectoryManager.HOLLOW_ENGINE.resolve("scripts").toFile()
        if (scriptsDir.exists()) {
            scriptsDir.walk()
                .filter { it.isFile && it.name.endsWith(".$UI_SCRIPT_EXTENSION") }
                .forEach { file ->
                    runCatching {
                        scripting.compiler.compile(file).getOrThrow().execute<UiScript>().getOrThrow()
                    }.onFailure { error ->
                        HollowEngine.LOGGER.error("Failed to execute UI script: {}", file.toReadablePath(), error)
                    }
                }
        }

        // The old composables belong to classes that no longer exist, so nothing shown may survive.
        UiScriptHudHost.reload()
    }
}
