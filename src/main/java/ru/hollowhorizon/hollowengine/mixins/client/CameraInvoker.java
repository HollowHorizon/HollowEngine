package ru.hollowhorizon.hollowengine.mixins.client;

import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Camera.class)
public interface CameraInvoker {
    @Invoker("setRotation")
    void rotate(float yRot, float xRot);

    @Invoker("setPosition")
    void position(double x, double y, double z);
}
