package ru.hollowhorizon.hollowengine.mixins.scripting;

import org.jetbrains.kotlin.com.intellij.util.containers.FList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = FList.class, remap = false)
public interface FListAccessor {
    @Accessor("myHead")
    public Object head();
}
