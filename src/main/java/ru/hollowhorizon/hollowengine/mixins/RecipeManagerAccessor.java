package ru.hollowhorizon.hollowengine.mixins;

import com.google.common.collect.Multimap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
//? if >=1.21
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(RecipeManager.class)
public interface RecipeManagerAccessor {
    //? if >=1.21 {
    @Accessor("byType")
    Multimap<RecipeType<?>, RecipeHolder<?>> hollowcore$getRecipes();

    @Accessor("byType")
    void hollowcore$setRecipes(Multimap<RecipeType<?>, RecipeHolder<?>> recipeMap);

    @Accessor("byName")
    Map<ResourceLocation, Recipe<?>> hollowcore$getByName();

    @Accessor("byName")
    void hollowcore$setByName(Map<ResourceLocation, Recipe<?>> byName);
    //?} else {
    /*@Accessor("recipes")
    Map<RecipeType<?>, Map<ResourceLocation, Recipe<?>>> hollowcore$getRecipes();

    @Accessor("recipes")
    void hollowcore$setRecipes(Map<RecipeType<?>, Map<ResourceLocation, Recipe<?>>> recipeMap);

    @Accessor("byName")
    Map<ResourceLocation, Recipe<?>> hollowcore$getByName();

    @Accessor("byName")
    void hollowcore$setByName(Map<ResourceLocation, Recipe<?>> byName);
    *///?}
}
