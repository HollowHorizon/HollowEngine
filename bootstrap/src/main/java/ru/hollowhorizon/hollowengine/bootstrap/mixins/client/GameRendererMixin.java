package ru.hollowhorizon.hollowengine.bootstrap.mixins.client;

import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.hollowhorizon.hollowengine.bootstrap.impl.BootstrapRuntimeManager;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void onGetFov(Camera camera, float partialTick, boolean changingFov, CallbackInfoReturnable<Double> cir) {
        double fov = BootstrapRuntimeManager.bridge().onCameraFov(
                (GameRenderer) (Object) this,
                camera,
                cir.getReturnValue(),
                partialTick,
                changingFov
        );
        cir.setReturnValue(fov);
    }
}
