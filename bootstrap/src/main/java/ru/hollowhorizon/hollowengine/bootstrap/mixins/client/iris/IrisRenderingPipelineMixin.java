package ru.hollowhorizon.hollowengine.bootstrap.mixins.client.iris;

import com.mojang.blaze3d.vertex.VertexFormat;
import net.irisshaders.iris.gl.blending.AlphaTest;
import net.irisshaders.iris.gl.state.FogMode;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.shaderpack.loading.ProgramId;
import net.irisshaders.iris.shaderpack.programs.ProgramSet;
import net.irisshaders.iris.shaderpack.programs.ProgramSource;
import net.minecraft.client.renderer.ShaderInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.hollowhorizon.hollowengine.fabric.internal.accessors.IrisRenderingPipelineAccessor;

import java.io.IOException;

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

    @Override
    @Invoker("createShader")
    public abstract ShaderInstance hollowengine$createShader(
            String name,
            ProgramSource source,
            ProgramId programId,
            AlphaTest fallbackAlpha,
            VertexFormat vertexFormat,
            FogMode fogMode,
            boolean isIntensity,
            boolean isFullbright,
            boolean isGlint,
            boolean isText,
            boolean isIE
    ) throws IOException;

    @Override
    @Invoker("createShadowShader")
    public abstract ShaderInstance hollowengine$createShadowShader(
            String name,
            ProgramSource source,
            ProgramId programId,
            AlphaTest fallbackAlpha,
            VertexFormat vertexFormat,
            boolean isIntensity,
            boolean isFullbright,
            boolean isText,
            boolean isIE
    ) throws IOException;
}
