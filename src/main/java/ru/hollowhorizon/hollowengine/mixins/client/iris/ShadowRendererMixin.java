package ru.hollowhorizon.hollowengine.mixins.client.iris;

import net.irisshaders.iris.mixin.LevelRendererAccessor;
import net.irisshaders.iris.shadows.ShadowRenderer;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.hollowhorizon.hollowengine.client.models.internal.rendering.InstanceBatchManager;

@Pseudo
@Mixin(value = ShadowRenderer.class, remap = false)
public class ShadowRendererMixin {
    @Inject(method = "renderShadows", at = @At("HEAD"), remap = false)
    private void hollowengine$clearShadowBatch(LevelRendererAccessor levelRenderer, Camera playerCamera, CallbackInfo ci) {
        InstanceBatchManager.INSTANCE.clear();
    }

    @Inject(
        method = "renderShadows",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;endBatch()V",
            shift = At.Shift.BEFORE,
            remap = false
        ),
        remap = false
    )
    private void hollowengine$flushShadowBatch(LevelRendererAccessor levelRenderer, Camera playerCamera, CallbackInfo ci) {
        InstanceBatchManager.INSTANCE.flush();
    }

    @Inject(method = "renderShadows", at = @At("RETURN"), remap = false)
    private void hollowengine$clearShadowBatchAfter(LevelRendererAccessor levelRenderer, Camera playerCamera, CallbackInfo ci) {
        InstanceBatchManager.INSTANCE.clear();
    }
}
