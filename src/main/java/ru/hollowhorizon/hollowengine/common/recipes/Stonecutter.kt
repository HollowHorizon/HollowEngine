package ru.hollowhorizon.hollowengine.common.recipes

import net.minecraft.tags.TagKey
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.crafting.Ingredient
import net.minecraft.world.item.crafting.RecipeType
import net.minecraft.world.item.crafting.StonecutterRecipe

object Stonecutter {
    fun addRecipe(
        output: ItemStack,
        input: Ingredient,
        group: String = ""
    ) {
        val recipe = StonecutterRecipe(RecipeHelper.createRecipeId(), group, input, output)
        RecipeHelper.currentScript?.addRecipe(recipe)
    }

    fun replaceRecipe(
        output: ItemStack,
        input: Ingredient,
        group: String = ""
    ) {
        removeRecipe(output)
        addRecipe(output, input, group)
    }

    fun addRecipe(
        output: ItemStack,
        input: ItemStack,
        group: String = ""
    ) = addRecipe(output, Ingredient.of(input), group)

    fun addRecipe(
        output: ItemStack,
        input: TagKey<Item>,
        group: String = ""
    ) = addRecipe(output, Ingredient.of(input), group)

    fun replaceRecipe(
        output: ItemStack,
        input: ItemStack,
        group: String = ""
    ) = replaceRecipe(output, Ingredient.of(input), group)

    fun replaceRecipe(
        output: ItemStack,
        input: TagKey<Item>,
        group: String = ""
    ) = replaceRecipe(output, Ingredient.of(input), group)

    fun removeRecipe(output: ItemStack) {
        RecipeHelper.currentScript?.removeByOutput(output, RecipeType.STONECUTTING)
    }
}