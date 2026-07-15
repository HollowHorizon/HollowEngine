package ru.hollowhorizon.hollowengine.bridge.mixins.client;

import net.minecraft.client.Camera;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Camera.class)
public interface CameraInvoker {
    @Invoker("setRotation")
    void hollowcore$rotate(float yaw, float pitch);

    @Invoker("setPosition")
    void hollowcore$setPosition(double x, double y, double z);

    @Accessor("rotation")
    Quaternionf hollowcore$getRotation();

    @Accessor("forwards")
    Vector3f hollowcore$getForwards();

    @Accessor("up")
    Vector3f hollowcore$getUp();

    @Accessor("left")
    Vector3f hollowcore$getLeft();
}
