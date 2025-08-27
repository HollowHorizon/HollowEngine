package ru.hollowhorizon.hollowengine.mixins.kool;

import de.fabmax.kool.input.CursorMode;
import de.fabmax.kool.input.CursorShape;
import de.fabmax.kool.input.PlatformInputJvm;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

@Mixin(value = PlatformInputJvm.class, remap = false)
public abstract class PlatformInputJvmMixin {
    @Shadow
    private static CursorShape currentCursorShape;
    @Final
    @Shadow
    private static Map<CursorShape, Long> cursorShapes;

    @Shadow
    protected abstract int getGlfwMode(CursorMode $this$glfwMode);

    @Inject(method = "applyCursorShape", at = @At("HEAD"), cancellable = true)
    private void onApplyCursorToShape(CursorShape cursorShape, CallbackInfo ci) {
        if (cursorShape != currentCursorShape) {
            GLFW.glfwSetCursor(Minecraft.getInstance().getWindow().getWindow(), cursorShapes.getOrDefault(cursorShape, 0L));
            currentCursorShape = cursorShape;
        }
        ci.cancel();
    }

    @Inject(method = "setCursorMode", at = @At("HEAD"), cancellable = true)
    private void onSetCursorMode(CursorMode cursorMode, CallbackInfo ci) {
        var x = new double[]{0.0};
        var y = new double[]{0.0};
        var windowHandle = Minecraft.getInstance().getWindow().getWindow();
        var mainTarget = Minecraft.getInstance().getMainRenderTarget();
        GLFW.glfwGetCursorPos(windowHandle, x, y);
        GLFW.glfwSetInputMode(windowHandle, GLFW.GLFW_CURSOR, getGlfwMode(cursorMode));
        if (cursorMode == CursorMode.NORMAL) {
            var setX = ((x[0] % mainTarget.width) + mainTarget.width) % mainTarget.width;
            var setY = ((y[0] % mainTarget.height) + mainTarget.height) % mainTarget.height;
            GLFW.glfwSetCursorPos(windowHandle, setX, setY);
        }
        ci.cancel();
    }
}
