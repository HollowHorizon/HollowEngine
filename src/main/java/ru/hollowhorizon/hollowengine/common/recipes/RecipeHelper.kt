package ru.hollowhorizon.hollowengine.common.recipes

import net.minecraft.resources.ResourceLocation
import ru.hollowhorizon.hollowengine.HollowEngine.Companion.MODID
import ru.hollowhorizon.hollowengine.common.scripting.content.ContentScriptBase

object RecipeHelper {
    var currentScript: ContentScriptBase? = null
    var latestId = 0

    fun createRecipeId() = ResourceLocation(MODID, "custom/recipe_${latestId++}")
}