package ru.hollowhorizon.hollowengine.bootstrap.mixins.client.iris;

import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.shaderpack.programs.ProgramSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.hollowhorizon.hollowengine.fabric.internal.accessors.IrisRenderingPipelineAccessor;

@Mixin(value = IrisRenderingPipeline.class, remap = false)
public abstract class IrisRenderingPipelineMixin implements IrisRenderingPipelineAccessor {
    @Unique
    private ProgramSet hollowengine$programSet;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void hollowengine$captureProgramSet(ProgramSet set, CallbackInfo ci) {
        hollowengine$programSet = set;
    }

    @Override
    public ProgramSet getProgramSet() {
        return hollowengine$programSet;
    }
}
