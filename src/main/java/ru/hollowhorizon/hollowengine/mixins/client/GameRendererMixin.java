package ru.hollowhorizon.hollowengine.mixins.client;

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
import ru.hollowhorizon.hc.common.events.EventBus;
import ru.hollowhorizon.hollowengine.client.render.CameraSetupEvent;

//TODO: Move to hollowcore
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    //? if fabric {
    @Inject(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setup(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/world/entity/Entity;ZZF)V", shift = At.Shift.AFTER), locals = LocalCapture.CAPTURE_FAILHARD)
    public void am$callCameraMove(float partialTicks, long finishTimeNano, PoseStack poseStack, CallbackInfo ci, boolean bl, Camera camera, PoseStack poseStack2, double d, float f, float g, Matrix4f matrix4f) {
        var event = new CameraSetupEvent((GameRenderer) (Object) this, camera, partialTicks, camera.getYRot(), camera.getXRot(), 0);
        EventBus.post(event);

        ((CameraInvoker) camera).rotate(event.getYaw(), event.getPitch());

        poseStack.mulPose(Axis.ZP.rotationDegrees(event.getRoll()));
    }
    //?}
}
