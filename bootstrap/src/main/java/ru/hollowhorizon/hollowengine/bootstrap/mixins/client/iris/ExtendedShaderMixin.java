package ru.hollowhorizon.hollowengine.bootstrap.mixins.client.iris;

import net.irisshaders.iris.pipeline.programs.ExtendedShader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.hollowhorizon.hollowengine.bootstrap.iris.IrisLocalShadowFramebufferHook;

@Mixin(ExtendedShader.class)
public class ExtendedShaderMixin {
    @Inject(method = {"apply", "method_34586", "m_173363_"}, at = @At("RETURN"), remap = false)
    private void hollowengine$restoreLocalShadowFramebuffer(CallbackInfo ci) {
        IrisLocalShadowFramebufferHook.rebindIfNeeded();
    }
}
