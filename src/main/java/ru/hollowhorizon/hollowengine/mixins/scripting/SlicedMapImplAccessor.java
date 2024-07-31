package ru.hollowhorizon.hollowengine.mixins.scripting;

import org.jetbrains.kotlin.com.intellij.util.keyFMap.KeyFMap;
import org.jetbrains.kotlin.util.slicedMap.SlicedMapImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(SlicedMapImpl.class)
public interface SlicedMapImplAccessor {
    @Accessor(value = "map", remap = false)
    Map<Object, KeyFMap> map();
}
