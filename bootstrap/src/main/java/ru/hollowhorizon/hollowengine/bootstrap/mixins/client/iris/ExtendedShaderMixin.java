package ru.hollowhorizon.hollowengine.bootstrap.mixins.client.iris;

import net.irisshaders.iris.pipeline.programs.ExtendedShader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.hollowhorizon.hollowengine.bootstrap.iris.IrisLocalShadowFramebufferHook;

@Pseudo
@Mixin(value = ExtendedShader.class, remap = false)
public class ExtendedShaderMixin {
    @Inject(method = "apply", at = @At("RETURN"), remap = false)
    private void hollowengine$restoreLocalShadowFramebuffer(CallbackInfo ci) {
        IrisLocalShadowFramebufferHook.rebindIfNeeded();
    }
}
