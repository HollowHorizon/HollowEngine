package ru.hollowhorizon.hollowengine.bootstrap.mixins.client.iris;

import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.hollowhorizon.hollowengine.bootstrap.iris.IrisLocalShadowFramebufferHook;

@Pseudo
@Mixin(value = IrisRenderingPipeline.class, remap = false)
public class SodiumTerrainPipelineMixin {
    @Inject(method = "bindDefaultShadow", at = @At("RETURN"))
    private void hollowengine$overrideLocalShadowFramebuffer(CallbackInfo ci) {
        IrisLocalShadowFramebufferHook.rebindIfNeeded();
    }
}
