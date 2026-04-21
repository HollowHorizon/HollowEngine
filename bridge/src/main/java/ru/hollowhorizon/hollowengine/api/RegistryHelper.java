package ru.hollowhorizon.hollowengine.api;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

public interface RegistryHelper {
    <T> ResourceKey<? extends Registry<T>> registry(Class<T> type);

    void addBlockModel(ResourceLocation location, AutoModelType model);
    void addItemModel(ResourceLocation location, AutoModelType model);
}
