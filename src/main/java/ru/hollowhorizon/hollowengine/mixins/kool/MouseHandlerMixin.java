package ru.hollowhorizon.hollowengine.mixins.kool;

import de.fabmax.kool.input.PointerInput;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.hollowhorizon.hollowengine.client.gui.scripting.ScriptingEnvironmentScreenKt;

@Mixin(MouseHandler.class)
public class MouseHandlerMixin {
    // TODO: Возможно, стоит сделать также поддержку glfwSetCursorEnterCallback (handleMouseExit)

    @Shadow private double xpos;

    @Shadow private double ypos;

    @Shadow @Final private Minecraft minecraft;
    @Unique
    private float hollowengine$x;
    @Unique
    private float hollowengine$y;

    @Inject(method = "onPress", at = @At("HEAD"), cancellable = true)
    private void onPress(long windowPointer, int button, int action, int modifiers, CallbackInfo ci) {
        PointerInput.INSTANCE.handleMouseButtonEvent$kool_core(button, action == GLFW.GLFW_PRESS);
        if(ScriptingEnvironmentScreenKt.isMouseOverDock(hollowengine$x, hollowengine$y) && minecraft.screen != null) {
            ci.cancel();
        }
    }

    @Inject(method = "onMove", at = @At("HEAD"), cancellable = true)
    private void onMove(long windowPointer, double xpos, double ypos, CallbackInfo ci) {
        com.mojang.blaze3d.platform.Window window = minecraft.getWindow();
        double scaleFactor = (double) minecraft.getMainRenderTarget().width / window.getScreenWidth();
        float convertedX = (float) (xpos * scaleFactor);
        float convertedY = (float) (ypos * scaleFactor);

        PointerInput.INSTANCE.handleMouseMove$kool_core((float) convertedX, (float) convertedY);
        hollowengine$x = (float) convertedX;
        hollowengine$y = (float) convertedY;
        if(ScriptingEnvironmentScreenKt.isMouseOverDock(hollowengine$x, hollowengine$y) && minecraft.screen != null) {
            this.xpos = 0;
            this.ypos = 0;
            ci.cancel();
        }
    }

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void onScroll(long windowPointer, double xOffset, double yOffset, CallbackInfo ci) {
        PointerInput.INSTANCE.handleMouseScroll$kool_core((float) xOffset, (float) yOffset);
        if(ScriptingEnvironmentScreenKt.isMouseOverDock(hollowengine$x, hollowengine$y) && minecraft.screen != null) {
            ci.cancel();
        }
    }
}
