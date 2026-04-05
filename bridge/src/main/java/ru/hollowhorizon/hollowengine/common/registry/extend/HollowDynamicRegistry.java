package ru.hollowhorizon.hollowengine.common.registry.extend;

import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings("unchecked")
public interface HollowDynamicRegistry {
    void hollow$clearDynamic();

    default void clearDynamic() {
        hollow$clearDynamic();
    }

    @Nullable ResourceKey<?> hollow$getKey(ResourceLocation id);

    default <T> @Nullable ResourceKey<T> getKey(ResourceLocation id) {
        return (ResourceKey<T>) hollow$getKey(id);
    }

    boolean hollow$isPresent(ResourceLocation id);

    default boolean isPresent(ResourceLocation id) {
        return hollow$isPresent(id);
    }
}
