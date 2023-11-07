package ru.hollowhorizon.hollowengine.common.recipes

import net.minecraft.tags.TagKey
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.crafting.BlastingRecipe
import net.minecraft.world.item.crafting.Ingredient
import net.minecraft.world.item.crafting.RecipeType

object BlastFurnace {
    fun addRecipe(
        output: ItemStack,
        input: Ingredient,
        group: String = "",
        experience: Float = 0f,
        cookingTime: Int = 200
    ) {
        val recipe = BlastingRecipe(RecipeHelper.createRecipeId(), group, input, output, experience, cookingTime)
        RecipeHelper.currentScript?.addRecipe(recipe)
    }

    fun replaceRecipe(
        output: ItemStack,
        input: Ingredient,
        group: String = "",
        experience: Float = 0f,
        cookingTime: Int = 200
    ) {
        removeRecipe(output)
        addRecipe(output, input, group, experience, cookingTime)
    }

    fun addRecipe(
        output: ItemStack,
        input: ItemStack,
        group: String = "",
        experience: Float = 0f,
        cookingTime: Int = 200
    ) = addRecipe(output, Ingredient.of(input), group, experience, cookingTime)

    fun addRecipe(
        output: ItemStack,
        input: TagKey<Item>,
        group: String = "",
        experience: Float = 0f,
        cookingTime: Int = 200
    ) = addRecipe(output, Ingredient.of(input), group, experience, cookingTime)

    fun replaceRecipe(
        output: ItemStack,
        input: ItemStack,
        group: String = "",
        experience: Float = 0f,
        cookingTime: Int = 200
    ) = replaceRecipe(output, Ingredient.of(input), group, experience, cookingTime)

    fun replaceRecipe(
        output: ItemStack,
        input: TagKey<Item>,
        group: String = "",
        experience: Float = 0f,
        cookingTime: Int = 200
    ) = replaceRecipe(output, Ingredient.of(input), group, experience, cookingTime)

    fun removeRecipe(output: ItemStack) {
        RecipeHelper.currentScript?.removeByOutput(output, RecipeType.BLASTING)
    }
}