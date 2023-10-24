package ru.hollowhorizon.hollowengine.mixins;

import net.minecraft.world.entity.ai.control.LookControl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Optional;

@Mixin(LookControl.class)
public interface LookControlInvoker {
    @Invoker("getXRotD")
    Optional<Float> invokeXRotD();

    @Invoker("getYRotD")
    Optional<Float> invokeYRotD();
}
