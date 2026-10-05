package ru.hollowhorizon.hollowengine.bootstrap.mixins.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.hollowhorizon.hollowengine.bootstrap.impl.BootstrapRuntimeManager;
import ru.hollowhorizon.hollowengine.bootstrap.runtime.RuntimeBridge;

@Mixin(Window.class)
public class WindowMixin {
    @Inject(method = "getWidth", at = @At("HEAD"), cancellable = true)
    private void hollowengine$getWidth(CallbackInfoReturnable<Integer> cir) {
        RuntimeBridge.GameViewportMetrics metrics = BootstrapRuntimeManager.bridge().getGameViewportMetrics();
        if (metrics != null && !BootstrapRuntimeManager.bridge().isGameViewportWindowPass()) {
            cir.setReturnValue(metrics.framebufferWidth());
        }
    }

    @Inject(method = "getHeight", at = @At("HEAD"), cancellable = true)
    private void hollowengine$getHeight(CallbackInfoReturnable<Integer> cir) {
        RuntimeBridge.GameViewportMetrics metrics = BootstrapRuntimeManager.bridge().getGameViewportMetrics();
        if (metrics != null && !BootstrapRuntimeManager.bridge().isGameViewportWindowPass()) {
            cir.setReturnValue(metrics.framebufferHeight());
        }
    }

    // Not a Redirect: GPU/performance mods hook the same call, and a second Redirect on it fails the launch.
    @WrapOperation(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/lwjgl/glfw/GLFW;glfwWindowHint(II)V"
            ),
            remap = false
    )
    private void overrideGlfwWindowHint(int target, int value, Operation<Void> original) {
        if (target == GLFW.GLFW_CONTEXT_VERSION_MAJOR || target == GLFW.GLFW_CONTEXT_VERSION_MINOR) {
            String versionText = BootstrapRuntimeManager.bridge().getOpenGlVersionOverride();
            if (versionText != null && versionText.contains(".")) {
                String[] version = versionText.split("\\.", 2);
                if (target == GLFW.GLFW_CONTEXT_VERSION_MAJOR) {
                    value = Integer.parseInt(version[0]);
                } else {
                    value = Integer.parseInt(version[1]);
                }
            }
        }
        original.call(target, value);
    }

    @Inject(method = "getGuiScale", at = @At("HEAD"), cancellable = true)
    public void getGuiScale(CallbackInfoReturnable<Double> cir) {
        RuntimeBridge.GameViewportMetrics metrics = BootstrapRuntimeManager.bridge().getGameViewportMetrics();
        if (metrics != null) {
            cir.setReturnValue(metrics.guiScale());
            return;
        }
        Window window = (Window) (Object) this;
        if (!BootstrapRuntimeManager.bridge().shouldForceAutoGuiScale(Minecraft.getInstance().screen)) return;
        cir.setReturnValue((double) window.calculateScale(0, Minecraft.getInstance().isEnforceUnicode()));
    }

    @Inject(method = "getGuiScaledHeight", at = @At("HEAD"), cancellable = true)
    public void getGuiScaledHeight(CallbackInfoReturnable<Integer> cir) {
        RuntimeBridge.GameViewportMetrics metrics = BootstrapRuntimeManager.bridge().getGameViewportMetrics();
        if (metrics != null) {
            cir.setReturnValue(metrics.guiScaledHeight());
            return;
        }
        Window window = (Window) (Object) this;
        if (!BootstrapRuntimeManager.bridge().shouldForceAutoGuiScale(Minecraft.getInstance().screen)) return;

        double scale = window.calculateScale(0, Minecraft.getInstance().isEnforceUnicode());
        int height = (int) (window.getHeight() / scale);
        cir.setReturnValue(window.getHeight() / scale > height ? height + 1 : height);
    }

    @Inject(method = "getGuiScaledWidth", at = @At("HEAD"), cancellable = true)
    public void getGuiScaledWidth(CallbackInfoReturnable<Integer> cir) {
        RuntimeBridge.GameViewportMetrics metrics = BootstrapRuntimeManager.bridge().getGameViewportMetrics();
        if (metrics != null) {
            cir.setReturnValue(metrics.guiScaledWidth());
            return;
        }
        Window window = (Window) (Object) this;
        if (!BootstrapRuntimeManager.bridge().shouldForceAutoGuiScale(Minecraft.getInstance().screen)) return;

        double scale = window.calculateScale(0, Minecraft.getInstance().isEnforceUnicode());
        int width = (int) (window.getWidth() / scale);
        cir.setReturnValue(window.getWidth() / scale > width ? width + 1 : width);
    }
}
