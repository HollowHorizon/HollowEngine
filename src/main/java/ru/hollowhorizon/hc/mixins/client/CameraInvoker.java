package ru.hollowhorizon.hc.mixins.client;

import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Camera.class)
public interface CameraInvoker {
    @Invoker("setRotation")
    void hollowcore$rotate(float yaw, float pitch); // Method to rotate the camera by specified yaw and pitch angles

    @Invoker("setPosition")
    void hollowcore$setPosition(double x, double y, double z); // Method to set the camera position in the world
}
