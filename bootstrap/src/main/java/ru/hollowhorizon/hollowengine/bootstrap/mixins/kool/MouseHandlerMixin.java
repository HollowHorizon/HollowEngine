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
    @Unique private float hollowengine$x;
    @Unique private float hollowengine$y;

    @Inject(method = "onPress", at = @At("HEAD"), cancellable = true)
    private void onPress(long windowPointer, int button, int action, int modifiers, CallbackInfo ci) {
        if (BootstrapRuntimeManager.bridge().onMousePress(minecraft, hollowengine$x, hollowengine$y, windowPointer, button, action, modifiers)) {
            ci.cancel();
        }
    }

    @Inject(method = "onMove", at = @At("HEAD"), cancellable = true)
    private void onMove(long windowPointer, double xpos, double ypos, CallbackInfo ci) {
        RuntimeBridge.MouseMoveResult result = BootstrapRuntimeManager.bridge().onMouseMove(minecraft, windowPointer, xpos, ypos);
        hollowengine$x = result.x();
        hollowengine$y = result.y();
        if (result.resetMousePosition()) {
            this.xpos = 0;
            this.ypos = 0;
        }
        if (result.cancel()) ci.cancel();
    }

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void onScroll(long windowPointer, double xOffset, double yOffset, CallbackInfo ci) {
        if (BootstrapRuntimeManager.bridge().onMouseScroll(minecraft, hollowengine$x, hollowengine$y, windowPointer, xOffset, yOffset)) {
            ci.cancel();
        }
    }
}
