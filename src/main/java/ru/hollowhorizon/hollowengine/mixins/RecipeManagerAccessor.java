package ru.hollowhorizon.hollowengine.mixins;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(RecipeManager.class)
public interface RecipeManagerAccessor {
    @Accessor("recipes")
    Map<RecipeType<?>, Map<ResourceLocation, Recipe<?>>> hollowcore$getRecipes();

    @Accessor("recipes")
    void hollowcore$setRecipes(Map<RecipeType<?>, Map<ResourceLocation, Recipe<?>>> recipeMap);

    @Accessor("byName")
    Map<ResourceLocation, Recipe<?>> hollowcore$getByName();

    @Accessor("byName")
    void hollowcore$setByName(Map<ResourceLocation, Recipe<?>> byName);
}
