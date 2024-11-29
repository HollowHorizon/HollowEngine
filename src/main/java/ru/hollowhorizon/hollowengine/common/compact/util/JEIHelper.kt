@file:JvmName("JEIHelper")

package ru.hollowhorizon.hollowengine.common.compact.util

import com.google.common.collect.Sets
import mezz.jei.api.recipe.IRecipeManager
import mezz.jei.api.recipe.category.IRecipeCategory
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.crafting.RecipeManager
import java.util.Optional
import java.util.stream.Collectors

@set:JvmName("setRecipeManager")
lateinit var recipeManagerProtected: RecipeManager

val recipeManager by lazy { recipeManagerProtected }

val IRecipeManager.hiddenCategories: Set<IRecipeCategory<*>>
    get() {
        val allCategories = this.createRecipeCategoryLookup().includeHidden().get().collect(Collectors.toSet())
        val visibleCategories = this.createRecipeCategoryLookup().get().collect(Collectors.toSet())
        return Sets.difference(allCategories, visibleCategories)
    }

val <T> IRecipeCategory<T>.recipeCategoryId: ResourceLocation
    get() = this.recipeType.uid

fun <T> IRecipeCategory<T>.hide(manager: IRecipeManager) {
    manager.hideRecipeCategory(this.recipeType)
}

fun <T> IRecipeCategory<T>.unhide(manager: IRecipeManager) {
    manager.unhideRecipeCategory(this.recipeType)
}

fun <T> IRecipeCategory<T>.hideWithin(recipe: T, manager: IRecipeManager) {
    manager.hideRecipes(this.recipeType, listOf(recipe))
}
