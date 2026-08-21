package ru.hollowhorizon.hollowengine.bootstrap.mixins.client.iris;

import net.irisshaders.iris.mixin.LevelRendererAccessor;
import net.irisshaders.iris.shadows.ShadowRenderer;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.hollowhorizon.hollowengine.bootstrap.impl.BootstrapRuntimeManager;

@Pseudo
@Mixin(value = ShadowRenderer.class)
public class ShadowRendererMixin {
    @Inject(method = "renderShadows", at = @At("HEAD"), remap = false)
    private void hollowengine$clearShadowBatch(LevelRendererAccessor levelRenderer, Camera playerCamera, CallbackInfo ci) {
        BootstrapRuntimeManager.bridge().onIrisShadowRenderStart();
    }

    @Inject(
            method = "renderShadows",
            at = @At(
                    value = "CONSTANT",
                    args = "stringValue=draw entities"), remap = false
    )
    private void hollowengine$flushShadowBatch(LevelRendererAccessor levelRenderer, Camera playerCamera, CallbackInfo ci) {
        BootstrapRuntimeManager.bridge().onIrisShadowRenderBeforeEndBatch();
    }

    @Inject(method = "renderShadows", at = @At("RETURN"), remap = false)
    private void hollowengine$clearShadowBatchAfter(LevelRendererAccessor levelRenderer, Camera playerCamera, CallbackInfo ci) {
        BootstrapRuntimeManager.bridge().onIrisShadowRenderEnd();
    }
}
