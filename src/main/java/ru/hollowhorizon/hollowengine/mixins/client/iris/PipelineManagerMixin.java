package ru.hollowhorizon.hollowengine.mixins.client.iris;

import net.irisshaders.iris.pipeline.PipelineManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.hollowhorizon.hollowengine.fabric.internal.IrisHelper;

@Mixin(value = PipelineManager.class, remap = false)
public class PipelineManagerMixin {
    @Inject(method = "destroyPipeline", at = @At("TAIL"))
    private void hollowengine$invalidatePrograms(CallbackInfo ci) {
        IrisHelper.invalidateInstancingPrograms();
    }
}
