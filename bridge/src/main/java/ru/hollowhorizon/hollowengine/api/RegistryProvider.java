package ru.hollowhorizon.hollowengine.api;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

public interface RegistryProvider<T> {
    RegistryHolder<T> register(
            @NotNull ResourceLocation location,
            @Nullable Registry<T> registry,
            @Nullable AutoModelType model,
            @NotNull Supplier<T> generator,
            @NotNull Class<T> type
    );
}
