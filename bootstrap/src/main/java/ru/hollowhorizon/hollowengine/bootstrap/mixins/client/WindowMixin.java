package ru.hollowhorizon.hollowengine.bootstrap.mixins.client;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.hollowhorizon.hollowengine.bootstrap.impl.BootstrapRuntimeManager;

@Mixin(Window.class)
public class WindowMixin {
    @Redirect(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/lwjgl/glfw/GLFW;glfwWindowHint(II)V"
            ),
            remap = false
    )
    private void redirectGlfwWindowHint(int target, int value) {
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
        GLFW.glfwWindowHint(target, value);
    }

    @Inject(method = "getGuiScale", at = @At("HEAD"), cancellable = true)
    public void getGuiScale(CallbackInfoReturnable<Double> cir) {
        Window window = (Window) (Object) this;
        if (!BootstrapRuntimeManager.bridge().shouldForceAutoGuiScale(Minecraft.getInstance().screen)) return;
        cir.setReturnValue((double) window.calculateScale(0, Minecraft.getInstance().isEnforceUnicode()));
    }

    @Inject(method = "getGuiScaledHeight", at = @At("HEAD"), cancellable = true)
    public void getGuiScaledHeight(CallbackInfoReturnable<Integer> cir) {
        Window window = (Window) (Object) this;
        if (!BootstrapRuntimeManager.bridge().shouldForceAutoGuiScale(Minecraft.getInstance().screen)) return;

        double scale = window.calculateScale(0, Minecraft.getInstance().isEnforceUnicode());
        int height = (int) (window.getHeight() / scale);
        cir.setReturnValue(window.getHeight() / scale > height ? height + 1 : height);
    }

    @Inject(method = "getGuiScaledWidth", at = @At("HEAD"), cancellable = true)
    public void getGuiScaledWidth(CallbackInfoReturnable<Integer> cir) {
        Window window = (Window) (Object) this;
        if (!BootstrapRuntimeManager.bridge().shouldForceAutoGuiScale(Minecraft.getInstance().screen)) return;

        double scale = window.calculateScale(0, Minecraft.getInstance().isEnforceUnicode());
        int width = (int) (window.getWidth() / scale);
        cir.setReturnValue(window.getWidth() / scale > width ? width + 1 : width);
    }
}
