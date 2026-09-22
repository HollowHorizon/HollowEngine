package ru.hollowhorizon.hollowengine.bootstrap.mixins.kool;

import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.hollowhorizon.hollowengine.bootstrap.impl.BootstrapRuntimeManager;
import ru.hollowhorizon.hollowengine.bootstrap.runtime.RuntimeBridge;

@Mixin(MouseHandler.class)
public class MouseHandlerMixin {
    @Shadow private double xpos;
    @Shadow private double ypos;
    @Shadow @Final private Minecraft minecraft;
    @Shadow private void onMove(long windowPointer, double xpos, double ypos) {
        throw new AssertionError("shadow");
    }

    @Unique private double hollowengine$windowX;
    @Unique private double hollowengine$windowY;
    @Unique private boolean hollowengine$hasWindowPosition;
    /** Set while a redirected move is being replayed, so the replay is not redirected again. */
    @Unique private boolean hollowengine$redirecting;

    @Inject(method = "onPress", at = @At("HEAD"), cancellable = true)
    private void onPress(long windowPointer, int button, int action, int modifiers, CallbackInfo ci) {
        double windowX = hollowengine$hasWindowPosition ? hollowengine$windowX : xpos;
        double windowY = hollowengine$hasWindowPosition ? hollowengine$windowY : ypos;
        if (BootstrapRuntimeManager.bridge().onMousePress(minecraft, windowX, windowY, windowPointer, button, action, modifiers)) {
            ci.cancel();
        }
    }

    @Inject(method = "onMove", at = @At("HEAD"), cancellable = true)
    private void hollowengine$onMove(long windowPointer, double xpos, double ypos, CallbackInfo ci) {
        if (hollowengine$redirecting) return;
        hollowengine$windowX = xpos;
        hollowengine$windowY = ypos;
        hollowengine$hasWindowPosition = true;
        RuntimeBridge.MouseMoveResult result = BootstrapRuntimeManager.bridge().onMouseMove(minecraft, windowPointer, xpos, ypos);
        if (result.resetMousePosition()) {
            this.xpos = 0;
            this.ypos = 0;
        }
        if (result.redirect()) {
            ci.cancel();
            hollowengine$redirecting = true;
            try {
                onMove(windowPointer, result.redirectX(), result.redirectY());
            } finally {
                hollowengine$redirecting = false;
            }
            return;
        }
        if (result.cancel()) ci.cancel();
    }

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void onScroll(long windowPointer, double xOffset, double yOffset, CallbackInfo ci) {
        double windowX = hollowengine$hasWindowPosition ? hollowengine$windowX : xpos;
        double windowY = hollowengine$hasWindowPosition ? hollowengine$windowY : ypos;
        if (BootstrapRuntimeManager.bridge().onMouseScroll(minecraft, windowX, windowY, windowPointer, xOffset, yOffset)) {
            ci.cancel();
        }
    }

    @Inject(method = "grabMouse", at = @At("HEAD"), cancellable = true)
    private void hollowengine$grabMouse(CallbackInfo ci) {
        if (!BootstrapRuntimeManager.bridge().allowMouseGrab()) ci.cancel();
    }
}
