package ru.hollowhorizon.hollowengine.mixins.client;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.hollowhorizon.hollowengine.HollowLoggerKt;
import ru.hollowhorizon.hollowengine.api.AutoScaled;
import ru.hollowhorizon.hollowengine.client.utils.HollowCoreLoader;
import ru.hollowhorizon.hollowengine.common.utils.JavaHacks;


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
            var version = HollowCoreLoader.INSTANCE.getConfig().getOpenGlVersion().split("\\.", 2);
            int major = Integer.parseInt(version[0]);
            int minor = Integer.parseInt(version[1]);

            var type = "unknown";
            if (target == GLFW.GLFW_CONTEXT_VERSION_MAJOR) {
                value = major;
                type = "major";
            } else if (target == GLFW.GLFW_CONTEXT_VERSION_MINOR) {
                value = minor;
                type = "minor";
            }
            HollowLoggerKt.getLOGGER().info("Setting OpenGL {} version to {}", type, value);
        }
        GLFW.glfwWindowHint(target, value);
    }

    @Inject(method = "getGuiScale", at = @At("HEAD"), cancellable = true)
    public void getGuiScale(CallbackInfoReturnable<Double> cir) {
        Window window = JavaHacks.forceCast(this);

        if (!(Minecraft.getInstance().screen instanceof AutoScaled)) return;

        cir.setReturnValue((double) window.calculateScale(0, Minecraft.getInstance().isEnforceUnicode()));
    }

    @Inject(method = "getGuiScaledHeight", at = @At("HEAD"), cancellable = true)
    public void getGuiScaledHeight(CallbackInfoReturnable<Integer> cir) {
        Window window = JavaHacks.forceCast(this);

        if (!(Minecraft.getInstance().screen instanceof AutoScaled)) return;

        double scale = window.calculateScale(0, Minecraft.getInstance().isEnforceUnicode());
        int height = (int) (window.getHeight() / scale);

        cir.setReturnValue((window.getHeight() / scale > height ? height + 1 : height));
    }

    @Inject(method = "getGuiScaledWidth", at = @At("HEAD"), cancellable = true)
    public void getGuiScaledWidth(CallbackInfoReturnable<Integer> cir) {
        Window window = JavaHacks.forceCast(this);

        if (!(Minecraft.getInstance().screen instanceof AutoScaled)) return;

        double scale = window.calculateScale(0, Minecraft.getInstance().isEnforceUnicode());
        int width = (int) (window.getWidth() / scale);

        cir.setReturnValue((window.getWidth() / scale > width ? width + 1 : width));
    }
}
