package ru.hollowhorizon.hollowengine.common.recipes

import net.minecraft.server.ReloadableServerResources
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.server.packs.resources.ResourceManagerReloadListener
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager
import ru.hollowhorizon.hollowengine.common.scripting.content.runContentScript
import ru.hollowhorizon.hollowengine.mixins.RecipeManagerAccessor

object RecipeReloadListener : ResourceManagerReloadListener {
    var resources: ReloadableServerResources? = null

    override fun onResourceManagerReload(manager: ResourceManager) {
        val recipeManager = resources?.recipeManager ?: return

        RecipeHelper.latestId = 0
        DirectoryManager.getContentScripts().forEach {
            runContentScript(recipeManager as RecipeManagerAccessor, it)
        }
    }
}