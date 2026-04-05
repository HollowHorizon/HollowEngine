package ru.hollowhorizon.hollowengine.common.utils

import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.crafting.Recipe
import net.minecraft.world.item.crafting.RecipeType

/**
 * Creates a simple RecipeType with the specified ID.
 *
 * @param id The ResourceLocation representing the recipe type ID.
 * @return A new RecipeType instance with the given ID.
 */
fun <T: Recipe<*>> simpleRecipeType(id: ResourceLocation) = object : RecipeType<T> {
    override fun toString(): String = id.toString()
}
