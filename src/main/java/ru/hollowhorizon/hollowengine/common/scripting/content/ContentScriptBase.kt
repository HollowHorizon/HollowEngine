package ru.hollowhorizon.hollowengine.common.scripting.content

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceLocation
import net.minecraft.tags.TagKey
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.crafting.Recipe
import net.minecraft.world.item.crafting.RecipeManager
import net.minecraft.world.item.crafting.RecipeType
import net.minecraftforge.common.crafting.conditions.ICondition
import net.minecraftforge.registries.ForgeRegistries
import ru.hollowhorizon.hc.client.utils.rl
import ru.hollowhorizon.hollowengine.common.recipes.RecipeHelper
import ru.hollowhorizon.hollowengine.common.recipes.RecipeReloadListener

open class ContentScriptBase(
    val recipes: MutableMap<RecipeType<*>, MutableMap<ResourceLocation, Recipe<*>>>,
    val byName: MutableMap<ResourceLocation, Recipe<*>>
) {

    init {
        RecipeHelper.currentScript = this
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

    fun item(item: String, count: Int = 1, nbt: CompoundTag? = null) = ItemStack(
        ForgeRegistries.ITEMS.getValue(item.rl) ?: throw IllegalStateException("Item $item not found!"),
        count,
        nbt
    )

    fun tag(tag: String): TagKey<Item> {
        val manager = ForgeRegistries.ITEMS.tags() ?: throw IllegalStateException("Tag $tag not found!")
        return manager.createTagKey(tag.rl)
    }
}