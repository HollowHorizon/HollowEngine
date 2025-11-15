package ru.hollowhorizon.hollowengine.mixins.kool;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;

@Mixin(targets = "de.fabmax.kool.modules.ui2.docking.DockNodeLeaf$LeafSlots", remap = false)
public abstract class MixinLeafSlots {

    /**
     * @author YourName
     * @reason Удаление центрального слота из композиции DockNodeLeaf$LeafSlots.compose()
     * Оригинальная строка: with(center) { composeSlot(sizeL, sizeL, marginV = sizes.smallGap) }
     */
    public void compose() {

    }
}