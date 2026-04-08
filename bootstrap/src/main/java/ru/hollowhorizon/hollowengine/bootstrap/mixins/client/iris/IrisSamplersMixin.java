package ru.hollowhorizon.hollowengine.bootstrap.mixins.client.iris;

import java.util.Set;
import net.irisshaders.iris.gl.image.GlImage;
import net.irisshaders.iris.gl.sampler.SamplerHolder;
import net.irisshaders.iris.samplers.IrisSamplers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.hollowhorizon.hollowengine.bootstrap.runtime.BootstrapRuntimeManager;

@Mixin(value = IrisSamplers.class, remap = false)
public class IrisSamplersMixin {
    @Inject(method = "addCustomImages", at = @At("HEAD"))
    private static void hollowengine$addClusteredLightingImages(
        SamplerHolder images,
        Set<GlImage> customImages,
        CallbackInfo ci
    ) {
        BootstrapRuntimeManager.bridge().onIrisAddCustomImages(customImages);
    }
}
