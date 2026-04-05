package ru.hollowhorizon.hollowengine.client.kool;

import de.fabmax.kool.input.PointerInput;

public final class KoolInputBridge {
    private KoolInputBridge() {
    }

    public static void handleMouseMove(float x, float y) {
        PointerInput.INSTANCE.handleMouseMove$kool_core(x, y);
    }

    public static void handleMouseButtonEvent(int button, boolean pressed) {
        PointerInput.INSTANCE.handleMouseButtonEvent$kool_core(button, pressed);
    }

    public static void handleMouseScroll(float xOffset, float yOffset) {
        PointerInput.INSTANCE.handleMouseScroll$kool_core(xOffset, yOffset);
    }
}
