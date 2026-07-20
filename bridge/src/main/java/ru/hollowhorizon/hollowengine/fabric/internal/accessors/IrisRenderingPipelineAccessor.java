package ru.hollowhorizon.hollowengine.fabric.internal.accessors;

import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.ShaderInstance;
import net.irisshaders.iris.gl.blending.AlphaTest;
import net.irisshaders.iris.gl.state.FogMode;
import net.irisshaders.iris.shaderpack.loading.ProgramId;
import net.irisshaders.iris.shaderpack.programs.ProgramSet;
import net.irisshaders.iris.shaderpack.programs.ProgramSource;

import java.io.IOException;

public interface IrisRenderingPipelineAccessor {
    ProgramSet getProgramSet();

    ShaderInstance hollowengine$createShader(
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

    ShaderInstance hollowengine$createShadowShader(
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
