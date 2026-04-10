package ru.hollowhorizon.hollowengine.bootstrap.mixins.client.iris;

import net.irisshaders.iris.gl.framebuffer.GlFramebuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.hollowhorizon.hollowengine.bootstrap.runtime.BootstrapRuntimeManager;

@Pseudo
@Mixin(targets = "net.irisshaders.iris.pipeline.SodiumTerrainPipeline", remap = false)
public class SodiumTerrainPipelineMixin {
    @Inject(method = "getShadowFramebuffer", at = @At("RETURN"), cancellable = true)
    private void hollowengine$overrideLocalShadowFramebuffer(CallbackInfoReturnable<GlFramebuffer> cir) {
        var bridge = BootstrapRuntimeManager.bridge();
        if (!bridge.isIrisLocalShadowPassActive()) {
            return;
        }

        Object framebuffer = bridge.getIrisLocalShadowFramebuffer();
        if (framebuffer instanceof GlFramebuffer glFramebuffer) {
            cir.setReturnValue(glFramebuffer);
        }
    }
}
