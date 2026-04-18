package ru.hollowhorizon.hollowengine.api.extensions;

import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public interface ItemStackHelper {
    @Nullable
    ItemStack getRecipeRemainerFor(ItemStack item);
}
