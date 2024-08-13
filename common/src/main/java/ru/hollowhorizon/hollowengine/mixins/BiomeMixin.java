package ru.hollowhorizon.hollowengine.mixins;

import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.hollowhorizon.hollowengine.client.gui.modificators.BiomeModificator;

@Mixin(Biome.class)
public class BiomeMixin {
    @Inject(method = "getSkyColor", at = @At("HEAD"), cancellable = true)
    private void skyColorModifier(CallbackInfoReturnable<Integer> cir) {
        if (BiomeModificator.INSTANCE.getEnable().get()) {
            cir.setReturnValue(BiomeModificator.INSTANCE.skyColor());
        }
    }

    @Inject(method = "getFogColor", at = @At("HEAD"), cancellable = true)
    private void fogColorModifier(CallbackInfoReturnable<Integer> cir) {
        if (BiomeModificator.INSTANCE.getEnable().get()) {
            cir.setReturnValue(BiomeModificator.INSTANCE.fogColor());
        }
    }

    @Inject(method = "getWaterColor", at = @At("HEAD"), cancellable = true)
    private void grassColorModifier(CallbackInfoReturnable<Integer> cir) {
        if (BiomeModificator.INSTANCE.getEnable().get()) {
            cir.setReturnValue(BiomeModificator.INSTANCE.waterColor());
        }
    }

    @Inject(method = "getWaterFogColor", at = @At("HEAD"), cancellable = true)
    private void foliageColorModifier(CallbackInfoReturnable<Integer> cir) {
        if (BiomeModificator.INSTANCE.getEnable().get()) {
            cir.setReturnValue(BiomeModificator.INSTANCE.waterFogColor());
        }
    }
}
