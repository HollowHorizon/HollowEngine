/*
 * MIT License
 *
 * Copyright (c) 2024 HollowHorizon
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package ru.hollowhorizon.hc.mixins.client;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.hollowhorizon.hc.HollowLoggerKt;
import ru.hollowhorizon.hc.api.AutoScaled;
import ru.hollowhorizon.hc.client.utils.HollowCoreLoader;
import ru.hollowhorizon.hc.common.utils.JavaHacks;


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
