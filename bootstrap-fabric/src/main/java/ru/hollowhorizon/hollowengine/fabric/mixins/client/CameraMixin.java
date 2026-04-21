package ru.hollowhorizon.hollowengine.fabric.mixins.client;

import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public class CameraMixin {
    @Inject(method = "setup", at=@At("HEAD"))
    private void onSetup(BlockGetter blockGetter, Entity entity, boolean bl, boolean bl2, float f, CallbackInfo ci) {
        //TODO: RuntimeBridge.CameraSetup setup = BootstrapRuntimeManager.bridge().onCameraSetup((GameRenderer) (Object) this, camera, partialTick);
        //        ((CameraInvoker) camera).hollowcore$rotate(setup.yaw(), setup.pitch(), <roll?!>);
    }
}
