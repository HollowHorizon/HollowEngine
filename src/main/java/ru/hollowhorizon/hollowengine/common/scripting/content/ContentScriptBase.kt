package ru.hollowhorizon.hollowengine.common.scripting.content

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.minecraft.world.item.crafting.Recipe
import net.minecraft.world.item.crafting.RecipeManager
import net.minecraftforge.common.crafting.conditions.ICondition
import ru.hollowhorizon.hc.client.utils.rl

open class ContentScriptBase(
    val recipeManager: RecipeManager
) {
    val recipes: MutableCollection<Recipe<*>>
        get() = recipeManager.recipes

    fun removeById(location: String) = recipes.removeIf { it.id == location.rl }

    fun addFromJson(name: String, data: String) = addFromJson(name, JsonParser.parseString(data).asJsonObject)

    fun addFromJson(name: String, data: JsonObject) {
        removeById(name)
        recipes.add(RecipeManager.fromJson(name.rl, data, ICondition.IContext.EMPTY))
    }
}