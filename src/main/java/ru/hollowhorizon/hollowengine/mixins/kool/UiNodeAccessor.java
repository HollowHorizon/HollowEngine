package ru.hollowhorizon.hollowengine.mixins.kool;

import de.fabmax.kool.modules.ui2.UiNode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(UiNode.class)
public interface UiNodeAccessor {
    @Accessor("scopeName")
    String getScopeName();
}
