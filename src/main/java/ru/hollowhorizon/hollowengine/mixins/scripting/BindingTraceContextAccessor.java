package ru.hollowhorizon.hollowengine.mixins.scripting;

import org.jetbrains.kotlin.resolve.BindingTraceContext;
import org.jetbrains.kotlin.util.slicedMap.MutableSlicedMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(BindingTraceContext.class)
public interface BindingTraceContextAccessor {
    @Accessor(value = "map", remap = false)
    MutableSlicedMap map();
}
