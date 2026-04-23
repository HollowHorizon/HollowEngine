package ru.hollowhorizon.hollowengine.bootstrap.mixins.client.iris;

import net.irisshaders.iris.pipeline.programs.SodiumShader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.hollowhorizon.hollowengine.bootstrap.iris.IrisLocalShadowFramebufferHook;

@Pseudo
@Mixin(value = SodiumShader.class, remap = false)
public class SodiumShaderMixin {
    @Inject(method = "setupState", at = @At("RETURN"))
    private void hollowengine$restoreLocalShadowFramebufferOnSetup(CallbackInfo ci) {
        IrisLocalShadowFramebufferHook.rebindIfNeeded();
    }

    @Inject(method = "resetState", at = @At("RETURN"))
    private void hollowengine$restoreLocalShadowFramebufferOnReset(CallbackInfo ci) {
        IrisLocalShadowFramebufferHook.rebindIfNeeded();
    }
}
