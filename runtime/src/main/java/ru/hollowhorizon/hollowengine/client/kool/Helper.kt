package ru.hollowhorizon.hollowengine.client.kool

import de.fabmax.kool.KoolContext
import de.fabmax.kool.input.KeyCode
import de.fabmax.kool.input.KeyboardInput
import de.fabmax.kool.pipeline.backend.gl.RenderBackendGl
import org.lwjgl.glfw.GLFW

val RenderBackendGl.ctx: KoolContext get() = KoolHooks.getContext(this)


@JvmField
val KEY_CODE_MAP: Map<Int, KeyCode> = mutableMapOf(
    GLFW.GLFW_KEY_LEFT_CONTROL to KeyboardInput.KEY_CTRL_LEFT,
    GLFW.GLFW_KEY_RIGHT_CONTROL to KeyboardInput.KEY_CTRL_RIGHT,
    GLFW.GLFW_KEY_LEFT_SHIFT to KeyboardInput.KEY_SHIFT_LEFT,
    GLFW.GLFW_KEY_RIGHT_SHIFT to KeyboardInput.KEY_SHIFT_RIGHT,
    GLFW.GLFW_KEY_LEFT_ALT to KeyboardInput.KEY_ALT_LEFT,
    GLFW.GLFW_KEY_RIGHT_ALT to KeyboardInput.KEY_ALT_RIGHT,
    GLFW.GLFW_KEY_LEFT_SUPER to KeyboardInput.KEY_SUPER_LEFT,
    GLFW.GLFW_KEY_RIGHT_SUPER to KeyboardInput.KEY_SUPER_RIGHT,
    GLFW.GLFW_KEY_ESCAPE to KeyboardInput.KEY_ESC,
    GLFW.GLFW_KEY_MENU to KeyboardInput.KEY_MENU,
    GLFW.GLFW_KEY_ENTER to KeyboardInput.KEY_ENTER,
    GLFW.GLFW_KEY_KP_ENTER to KeyboardInput.KEY_NP_ENTER,
    GLFW.GLFW_KEY_KP_DIVIDE to KeyboardInput.KEY_NP_DIV,
    GLFW.GLFW_KEY_KP_MULTIPLY to KeyboardInput.KEY_NP_MUL,
    GLFW.GLFW_KEY_KP_ADD to KeyboardInput.KEY_NP_PLUS,
    GLFW.GLFW_KEY_KP_SUBTRACT to KeyboardInput.KEY_NP_MINUS,
    GLFW.GLFW_KEY_KP_DECIMAL to KeyboardInput.KEY_NP_DECIMAL,
    GLFW.GLFW_KEY_BACKSPACE to KeyboardInput.KEY_BACKSPACE,
    GLFW.GLFW_KEY_TAB to KeyboardInput.KEY_TAB,
    GLFW.GLFW_KEY_DELETE to KeyboardInput.KEY_DEL,
    GLFW.GLFW_KEY_INSERT to KeyboardInput.KEY_INSERT,
    GLFW.GLFW_KEY_HOME to KeyboardInput.KEY_HOME,
    GLFW.GLFW_KEY_END to KeyboardInput.KEY_END,
    GLFW.GLFW_KEY_PAGE_UP to KeyboardInput.KEY_PAGE_UP,
    GLFW.GLFW_KEY_PAGE_DOWN to KeyboardInput.KEY_PAGE_DOWN,
    GLFW.GLFW_KEY_LEFT to KeyboardInput.KEY_CURSOR_LEFT,
    GLFW.GLFW_KEY_RIGHT to KeyboardInput.KEY_CURSOR_RIGHT,
    GLFW.GLFW_KEY_UP to KeyboardInput.KEY_CURSOR_UP,
    GLFW.GLFW_KEY_DOWN to KeyboardInput.KEY_CURSOR_DOWN,
    GLFW.GLFW_KEY_F1 to KeyboardInput.KEY_F1,
    GLFW.GLFW_KEY_F2 to KeyboardInput.KEY_F2,
    GLFW.GLFW_KEY_F3 to KeyboardInput.KEY_F3,
    GLFW.GLFW_KEY_F4 to KeyboardInput.KEY_F4,
    GLFW.GLFW_KEY_F5 to KeyboardInput.KEY_F5,
    GLFW.GLFW_KEY_F6 to KeyboardInput.KEY_F6,
    GLFW.GLFW_KEY_F7 to KeyboardInput.KEY_F7,
    GLFW.GLFW_KEY_F8 to KeyboardInput.KEY_F8,
    GLFW.GLFW_KEY_F9 to KeyboardInput.KEY_F9,
    GLFW.GLFW_KEY_F10 to KeyboardInput.KEY_F10,
    GLFW.GLFW_KEY_F11 to KeyboardInput.KEY_F11,
    GLFW.GLFW_KEY_F12 to KeyboardInput.KEY_F12
)

