package ru.hollowhorizon.hollowengine.client.kool;

import de.fabmax.kool.input.*;
import org.lwjgl.glfw.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static ru.hollowhorizon.hollowengine.client.kool.HelperKt.KEY_CODE_MAP;

public class PointerInputSetup {
    public static final HashMap<Integer, Integer> localCharKeyCodes = new HashMap<>();

    static {
        deriveLocalKeyCodes();
    }

    private static void deriveLocalKeyCodes() {
        List<Integer> printableKeys = new ArrayList<>();

        // Добавляем цифровые клавиши
        for (int c = GLFW.GLFW_KEY_0; c <= GLFW.GLFW_KEY_9; c++) {
            printableKeys.add(c);
        }
        // Добавляем буквенные клавиши
        for (int c = GLFW.GLFW_KEY_A; c <= GLFW.GLFW_KEY_Z; c++) {
            printableKeys.add(c);
        }
        // Добавляем клавиши на цифровой клавиатуре
        for (int c = GLFW.GLFW_KEY_KP_0; c <= GLFW.GLFW_KEY_KP_9; c++) {
            printableKeys.add(c);
        }

        // Добавляем другие клавиши
        printableKeys.add(GLFW.GLFW_KEY_APOSTROPHE);
        printableKeys.add(GLFW.GLFW_KEY_COMMA);
        printableKeys.add(GLFW.GLFW_KEY_MINUS);
        printableKeys.add(GLFW.GLFW_KEY_PERIOD);
        printableKeys.add(GLFW.GLFW_KEY_SLASH);
        printableKeys.add(GLFW.GLFW_KEY_SEMICOLON);
        printableKeys.add(GLFW.GLFW_KEY_EQUAL);
        printableKeys.add(GLFW.GLFW_KEY_LEFT_BRACKET);
        printableKeys.add(GLFW.GLFW_KEY_RIGHT_BRACKET);
        printableKeys.add(GLFW.GLFW_KEY_BACKSLASH);
        printableKeys.add(GLFW.GLFW_KEY_KP_DECIMAL);
        printableKeys.add(GLFW.GLFW_KEY_KP_DIVIDE);
        printableKeys.add(GLFW.GLFW_KEY_KP_MULTIPLY);
        printableKeys.add(GLFW.GLFW_KEY_KP_SUBTRACT);
        printableKeys.add(GLFW.GLFW_KEY_KP_ADD);
        printableKeys.add(GLFW.GLFW_KEY_KP_EQUAL);

        // Обработка клавиш
        for (int c : printableKeys) {
            String localName = GLFW.glfwGetKeyName(c, 0);
            if (localName != null && !localName.isBlank()) {
                char localChar = Character.toUpperCase(localName.charAt(0));
                localCharKeyCodes.put(c, (int) localChar);
            }
        }
    }

    public static int getKeyMod(int key, int mods, int event) {
        int keyMod = 0;

        if ((mods & GLFW.GLFW_MOD_ALT) != 0) keyMod |= KeyboardInput.KEY_MOD_ALT;
        if ((mods & GLFW.GLFW_MOD_CONTROL) != 0) keyMod |= KeyboardInput.KEY_MOD_CTRL;
        if ((mods & GLFW.GLFW_MOD_SHIFT) != 0) keyMod |= KeyboardInput.KEY_MOD_SHIFT;
        if ((mods & GLFW.GLFW_MOD_SUPER) != 0) keyMod |= KeyboardInput.KEY_MOD_SUPER;

        switch (key) {
            case GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_KEY_RIGHT_SHIFT ->
                    keyMod = updateDownMask(keyMod, KeyboardInput.KEY_MOD_SHIFT, event);
            case GLFW.GLFW_KEY_LEFT_CONTROL, GLFW.GLFW_KEY_RIGHT_CONTROL ->
                    keyMod = updateDownMask(keyMod, KeyboardInput.KEY_MOD_CTRL, event);
            case GLFW.GLFW_KEY_LEFT_ALT, GLFW.GLFW_KEY_RIGHT_ALT ->
                    keyMod = updateDownMask(keyMod, KeyboardInput.KEY_MOD_ALT, event);
            case GLFW.GLFW_KEY_LEFT_SUPER, GLFW.GLFW_KEY_RIGHT_SUPER ->
                    keyMod = updateDownMask(keyMod, KeyboardInput.KEY_MOD_SUPER, event);
        }
        return keyMod;
    }

    public static int updateDownMask(int mask, int bit, int event) {
        if ((event & KeyboardInput.KEY_EV_DOWN) != 0) {
            return mask | bit;
        } else {
            return mask & ~bit;
        }
    }

}
