package ru.hollowhorizon.hollowengine.bootstrap.mixins.client.iris;

import net.irisshaders.iris.pipeline.programs.FallbackShader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.hollowhorizon.hollowengine.bootstrap.iris.IrisLocalShadowFramebufferHook;

@Pseudo
@Mixin(value = FallbackShader.class)
public class FallbackShaderMixin {
    @Inject(method = {"apply", "method_34586", "m_173363_"}, at = @At("RETURN"), remap = false)
    private void hollowengine$restoreLocalShadowFramebuffer(CallbackInfo ci) {
        IrisLocalShadowFramebufferHook.rebindIfNeeded();
    }
}
