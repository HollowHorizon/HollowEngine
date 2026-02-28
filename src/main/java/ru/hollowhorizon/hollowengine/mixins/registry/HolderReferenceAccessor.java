package ru.hollowhorizon.hollowengine.mixins.registry;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Holder.Reference.class)
public interface HolderReferenceAccessor<T> {
    @Invoker("bindKey")
    void hollow$bindKey(ResourceKey<T> key);

    @Invoker("bindValue")
    void hollow$bindValue(T value);
}
