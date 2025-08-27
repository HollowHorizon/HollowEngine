package ru.hollowhorizon.hc.mixins.kool;

import de.fabmax.kool.input.PointerInput;
import net.minecraft.client.MouseHandler;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class MouseHandlerMixin {
    // TODO: Возможно, стоит сделать также поддержку glfwSetCursorEnterCallback (handleMouseExit)

    @Inject(method = "onPress", at = @At("HEAD"))
    private void onPress(long windowPointer, int button, int action, int modifiers, CallbackInfo ci) {
        PointerInput.INSTANCE.handleMouseButtonEvent$kool_core(button, action == GLFW.GLFW_PRESS);
    }

    @Inject(method = "onMove", at = @At("HEAD"))
    private void onMove(long windowPointer, double xpos, double ypos, CallbackInfo ci) {
        PointerInput.INSTANCE.handleMouseMove$kool_core((float) xpos, (float) ypos);
    }

    @Inject(method = "onScroll", at = @At("HEAD"))
    private void onScroll(long windowPointer, double xOffset, double yOffset, CallbackInfo ci) {
        PointerInput.INSTANCE.handleMouseScroll$kool_core((float) xOffset, (float) yOffset);
    }
}
