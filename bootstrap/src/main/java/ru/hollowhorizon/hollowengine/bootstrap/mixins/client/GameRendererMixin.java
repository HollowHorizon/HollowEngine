package ru.hollowhorizon.hollowengine.bootstrap.mixins.client;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;
import ru.hollowhorizon.hollowengine.bootstrap.impl.BootstrapRuntimeManager;
import ru.hollowhorizon.hollowengine.bootstrap.runtime.RuntimeBridge;
import ru.hollowhorizon.hollowengine.bridge.mixins.client.CameraInvoker;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(
        method = "renderLevel",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setup(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/world/entity/Entity;ZZF)V", shift = At.Shift.AFTER),
        locals = LocalCapture.CAPTURE_FAILHARD
    )
    private void onCameraSetup(DeltaTracker deltaTracker, CallbackInfo ci, float partialTick, boolean bl, Camera camera, Entity entity, float g) {
        RuntimeBridge.CameraSetup setup = BootstrapRuntimeManager.bridge().onCameraSetup((GameRenderer) (Object) this, camera, partialTick);
        ((CameraInvoker) camera).hollowcore$rotate(setup.yaw(), setup.pitch());

        // TODO: Нужно разобраться, как теперь вращать roll
        // poseStack.mulPose(Axis.ZP.rotationDegrees(setup.roll()));
    }
}
