package ru.hollowhorizon.hollowengine.bootstrap.mixins.kool;

import net.minecraft.client.KeyboardHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.hollowhorizon.hollowengine.bootstrap.runtime.BootstrapRuntimeManager;

@Mixin(KeyboardHandler.class)
public class KeyboardHandlerMixin {
    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void onKey(long windowPointer, int key, int scanCode, int action, int modifiers, CallbackInfo ci) {
        if (BootstrapRuntimeManager.bridge().onKeyboardKey(windowPointer, key, scanCode, action, modifiers)) {
            ci.cancel();
        }
    }

    @Inject(method = "charTyped", at = @At("HEAD"), cancellable = true)
    private void onChar(long windowPointer, int codePoint, int modifiers, CallbackInfo ci) {
        if (BootstrapRuntimeManager.bridge().onKeyboardChar(windowPointer, codePoint, modifiers)) {
            ci.cancel();
        }
    }
}
