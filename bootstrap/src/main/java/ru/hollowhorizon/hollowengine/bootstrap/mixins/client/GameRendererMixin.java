package ru.hollowhorizon.hollowengine.bootstrap.mixins.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;
import ru.hollowhorizon.hollowengine.bootstrap.runtime.BootstrapRuntimeManager;
import ru.hollowhorizon.hollowengine.bootstrap.runtime.RuntimeBridge;
import ru.hollowhorizon.hollowengine.bridge.mixins.client.CameraInvoker;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(
        method = "renderLevel",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setup(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/world/entity/Entity;ZZF)V", shift = At.Shift.AFTER),
        locals = LocalCapture.CAPTURE_FAILHARD
    )
    private void onCameraSetup(float partialTicks, long finishTimeNano, PoseStack poseStack, CallbackInfo ci, boolean renderBlockOutline, Camera camera, PoseStack poseStack2, double d, float f, float g, Matrix4f matrix4f) {
        RuntimeBridge.CameraSetup setup = BootstrapRuntimeManager.bridge().onCameraSetup((GameRenderer) (Object) this, camera, partialTicks);
        ((CameraInvoker) camera).hollowcore$rotate(setup.yaw(), setup.pitch());
        poseStack.mulPose(Axis.ZP.rotationDegrees(setup.roll()));
    }
}
