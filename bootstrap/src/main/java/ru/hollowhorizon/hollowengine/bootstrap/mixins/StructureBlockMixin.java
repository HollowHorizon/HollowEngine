package ru.hollowhorizon.hollowengine.bootstrap.mixins;

import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.StructureBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(StructureBlockEntity.class)
public class StructureBlockMixin {
    //? if >= 1.21 {
    /*@Redirect(method = "loadAdditional", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;clamp(III)I"))
    *///?} else {
    @Redirect(method = "load", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;clamp(III)I"))
    //?}
    public int hollowengine$read(int value, int min, int max) {
        return Mth.clamp(value, -500, 500);
    }
}
