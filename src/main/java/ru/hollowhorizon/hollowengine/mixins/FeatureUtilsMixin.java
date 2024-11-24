package ru.hollowhorizon.hollowengine.mixins;

import net.minecraft.data.worldgen.BootstapContext;
import net.minecraft.data.worldgen.features.FeatureUtils;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.hollowhorizon.hollowengine.common.world.FeatureManager;

@Mixin(FeatureUtils.class)
public class FeatureUtilsMixin {
    @Inject(method = "bootstrap", at = @At("TAIL"))
    private static void onBootstrap(BootstapContext<ConfiguredFeature<?, ?>> context, CallbackInfo ci) {
        FeatureManager.INSTANCE.onReload(context);
    }
}
