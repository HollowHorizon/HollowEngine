package ru.hollowhorizon.hollowengine.mixins.kool;

import de.fabmax.kool.input.*;
import net.minecraft.client.KeyboardHandler;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.hollowhorizon.hollowengine.client.kool.PointerInputSetup;

import static ru.hollowhorizon.hollowengine.client.kool.HelperKt.KEY_CODE_MAP;

@Mixin(KeyboardHandler.class)
public class KeyboardHandlerMixin {
    @Inject(method = "keyPress", at = @At("HEAD"))
    private void onKey(long windowPointer, int key, int scanCode, int action, int modifiers, CallbackInfo ci) {
        int event = switch (action) {
            case GLFW.GLFW_PRESS -> KeyboardInput.KEY_EV_DOWN;
            case GLFW.GLFW_REPEAT -> KeyboardInput.KEY_EV_DOWN | KeyboardInput.KEY_EV_REPEATED;
            case GLFW.GLFW_RELEASE -> KeyboardInput.KEY_EV_UP;
            default -> -1;
        };

        if (event != -1) {
            KeyCode keyCode = KEY_CODE_MAP.getOrDefault(key, new UniversalKeyCode(key, null));
            LocalKeyCode localKeyCode = new LocalKeyCode(PointerInputSetup.localCharKeyCodes.getOrDefault(keyCode.getCode(), keyCode.getCode()), null);
            int keyMod = PointerInputSetup.getKeyMod(key, modifiers, event);

            KeyboardInput.INSTANCE.handleKeyEvent(new KeyEvent(keyCode, localKeyCode, event, keyMod, Character.MIN_VALUE));
        }
    }

    @Inject(method = "charTyped", at = @At("HEAD"))
    private void onChar(long windowPointer, int codePoint, int modifiers, CallbackInfo ci) {
        KeyboardInput.INSTANCE.handleCharTyped((char) codePoint);
    }
}
