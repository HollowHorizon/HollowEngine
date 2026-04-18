package ru.hollowhorizon.hollowengine.bridge.mixins;

import com.google.common.collect.Multimap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(RecipeManager.class)
public interface RecipeManagerAccessor {
    @Accessor("byType")
    Multimap<RecipeType<?>, RecipeHolder<?>> hollowcore$getRecipes();

    @Accessor("byType")
    void hollowcore$setRecipes(Multimap<RecipeType<?>, RecipeHolder<?>> recipeMap);

    @Accessor("byName")
    Map<ResourceLocation, RecipeHolder<?>> hollowcore$getByName();

    @Accessor("byName")
    void hollowcore$setByName(Map<ResourceLocation, RecipeHolder<?>> byName);
}
