package ru.hollowhorizon.hollowengine.mixins.client.iris;

import net.irisshaders.iris.gl.blending.BlendModeOverride;
import net.irisshaders.iris.shaderpack.programs.ProgramSet;
import net.irisshaders.iris.shaderpack.programs.ProgramSource;
import net.irisshaders.iris.shaderpack.properties.ShaderProperties;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.hollowhorizon.hollowengine.fabric.internal.accessors.ProgramSourceAccessor;

@Mixin(value = ProgramSource.class, remap = false)
public class ProgramSourceMixin implements ProgramSourceAccessor {
    @Unique
    private ShaderProperties hollowengine$shaderProperties;

    @Unique
    private BlendModeOverride hollowengine$blendModeOverride;

    @Inject(
        method = "<init>(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Lnet/irisshaders/iris/shaderpack/programs/ProgramSet;Lnet/irisshaders/iris/shaderpack/properties/ShaderProperties;Lnet/irisshaders/iris/gl/blending/BlendModeOverride;)V",
        at = @At("TAIL")
    )
    private void hollowengine$captureProperties(String name, String vertexSource, String geometrySource, String tessControlSource,
                                                String tessEvalSource, String fragmentSource, ProgramSet parent,
                                                ShaderProperties properties, BlendModeOverride defaultBlendModeOverride, CallbackInfo ci) {
        hollowengine$shaderProperties = properties;
        hollowengine$blendModeOverride = defaultBlendModeOverride;
    }

    @Override
    public ShaderProperties getShaderPropertiesValue() {
        return hollowengine$shaderProperties;
    }

    @Override
    public BlendModeOverride getBlendModeOverrideValue() {
        return hollowengine$blendModeOverride;
    }
}
