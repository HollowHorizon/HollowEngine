package ru.hollowhorizon.hollowengine.common.scripting.content

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.crafting.Recipe
import net.minecraft.world.item.crafting.RecipeManager
import net.minecraft.world.item.crafting.RecipeType
import net.minecraftforge.common.crafting.conditions.ICondition
import ru.hollowhorizon.hc.client.utils.isPhysicalClient
import ru.hollowhorizon.hc.client.utils.mcTranslate
import ru.hollowhorizon.hc.client.utils.rl
import ru.hollowhorizon.hollowengine.client.ClientEvents
import ru.hollowhorizon.hollowengine.common.recipes.RecipeHelper
import ru.hollowhorizon.hollowengine.common.recipes.RecipeReloadListener

open class ContentScriptBase(
    val recipes: MutableMap<RecipeType<*>, MutableMap<ResourceLocation, Recipe<*>>>,
    val byName: MutableMap<ResourceLocation, Recipe<*>>
) {
    val mods = Mods

    init {
        RecipeHelper.currentScript = this
    }

    fun ItemStack.tooltip(text: String): ItemStack {
        if (isPhysicalClient) ClientEvents.addTooltip(this.item, text.mcTranslate)
        return this
    }

    fun removeById(location: String) {
        recipes.values.forEach { recipe ->
            recipe.remove(location.rl)
        }
        byName.remove(location.rl)
    }

    fun removeByOutput(output: ItemStack, type: RecipeType<*>, checkTag: Boolean = false) {
        val ids = arrayListOf<ResourceLocation>()
        recipes[type]?.forEach { recipe ->
            val value = recipe.value
            if ((value.resultItem.item == output.item) && (!checkTag || value.resultItem.tag == output.tag) && (value.type == type)) {
                ids += recipe.key
            }
        }
        recipes.values.forEach { recipe ->
            ids.forEach {
                recipe.remove(it)
            }
        }
        ids.forEach(byName::remove)
    }

    fun addFromJson(name: String, data: String) = addFromJson(name, JsonParser.parseString(data).asJsonObject)

    fun addFromJson(name: String, data: JsonObject) {
        removeById(name)
        val recipe = RecipeManager.fromJson(
            name.rl,
            data,
            RecipeReloadListener.resources?.conditionContext ?: ICondition.IContext.EMPTY
        )
        addRecipe(recipe)
    }

    fun addRecipe(recipe: Recipe<*>) {
        recipes.computeIfAbsent(recipe.type) { hashMapOf() }[recipe.id] = recipe
        byName[recipe.id] = recipe
    }
}

object Mods {
    operator fun set(modid: String, name: String) {
        if(isPhysicalClient) ClientEvents.setModName(modid, name)
    }
}