package ru.hollowhorizon.hollowengine.bootstrap.mixins.client.iris;

import net.irisshaders.iris.gl.state.FogMode;
import net.irisshaders.iris.gl.uniform.DynamicUniformHolder;
import net.irisshaders.iris.uniforms.CommonUniforms;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.hollowhorizon.hollowengine.bootstrap.runtime.BootstrapRuntimeManager;

@Mixin(value = CommonUniforms.class, remap = false)
public class CommonUniformsMixin {
    @Inject(method = "addDynamicUniforms", at = @At("RETURN"))
    private static void hollowengine$addClusteredLightingUniforms(
        DynamicUniformHolder uniforms,
        FogMode fogMode,
        CallbackInfo ci
    ) {
        BootstrapRuntimeManager.bridge().onIrisAddDynamicUniforms(uniforms);
    }
}
