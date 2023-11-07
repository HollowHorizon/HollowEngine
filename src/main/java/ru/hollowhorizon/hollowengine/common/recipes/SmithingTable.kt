package ru.hollowhorizon.hollowengine.common.recipes

import net.minecraft.tags.TagKey
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.crafting.Ingredient
import net.minecraft.world.item.crafting.RecipeType
import net.minecraft.world.item.crafting.UpgradeRecipe


object SmithingTable {
    fun addRecipe(result: ItemStack, input: Ingredient, addition: Ingredient) {
        RecipeHelper.currentScript?.addRecipe(
            UpgradeRecipe(RecipeHelper.createRecipeId(), input, addition, result)
        )
    }

    fun addRecipe(result: ItemStack, input: ItemStack, addition: ItemStack) =
        addRecipe(result, Ingredient.of(input), Ingredient.of(addition))

    fun addRecipe(result: ItemStack, input: TagKey<Item>, addition: TagKey<Item>) =
        addRecipe(result, Ingredient.of(input), Ingredient.of(addition))

    fun addRecipe(result: ItemStack, input: TagKey<Item>, addition: ItemStack) =
        addRecipe(result, Ingredient.of(input), Ingredient.of(addition))

    fun addRecipe(result: ItemStack, input: ItemStack, addition: TagKey<Item>) =
        addRecipe(result, Ingredient.of(input), Ingredient.of(addition))

    fun replaceRecipe(result: ItemStack, input: ItemStack, addition: ItemStack) {
        removeRecipe(result)
        addRecipe(result, Ingredient.of(input), Ingredient.of(addition))
    }

    fun replaceRecipe(result: ItemStack, input: TagKey<Item>, addition: TagKey<Item>) {
        removeRecipe(result)
        addRecipe(result, Ingredient.of(input), Ingredient.of(addition))
    }

    fun replaceRecipe(result: ItemStack, input: TagKey<Item>, addition: ItemStack) {
        removeRecipe(result)
        addRecipe(result, Ingredient.of(input), Ingredient.of(addition))
    }

    fun replaceRecipe(result: ItemStack, input: ItemStack, addition: TagKey<Item>) {
        removeRecipe(result)
        addRecipe(result, Ingredient.of(input), Ingredient.of(addition))
    }

    fun removeRecipe(result: ItemStack, checkTag: Boolean = false) {
        RecipeHelper.currentScript?.removeByOutput(result, RecipeType.SMITHING, checkTag)
    }
}