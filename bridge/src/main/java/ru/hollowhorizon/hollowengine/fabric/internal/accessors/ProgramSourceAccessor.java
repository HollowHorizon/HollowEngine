package ru.hollowhorizon.hollowengine.fabric.internal.accessors;

import net.irisshaders.iris.gl.blending.BlendModeOverride;
import net.irisshaders.iris.shaderpack.properties.ShaderProperties;

public interface ProgramSourceAccessor {
    ShaderProperties getShaderPropertiesValue();

    BlendModeOverride getBlendModeOverrideValue();
}
