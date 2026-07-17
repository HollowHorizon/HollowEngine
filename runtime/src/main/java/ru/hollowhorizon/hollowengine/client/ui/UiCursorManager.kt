package ru.hollowhorizon.hollowengine.client.ui

import org.lwjgl.glfw.GLFW.*

/**
 * Applies a [UiCursorShape] to a GLFW window. Standard cursors are created lazily and cached;
 * the window cursor is only changed when the shape actually differs, so this is cheap to call
 * every frame with the node currently under the pointer.
 */
object UiCursorManager {
    private val cursors = HashMap<UiCursorShape, Long>()
    private var current: UiCursorShape? = null

    fun apply(window: Long, shape: UiCursorShape) {
        if (window == 0L || shape == current) return
        current = shape
        glfwSetCursor(window, cursorHandle(shape))
    }

    /** Resets to the default arrow — call when the UI stops owning the cursor. */
    fun reset(window: Long) = apply(window, UiCursorShape.DEFAULT)

    private fun cursorHandle(shape: UiCursorShape): Long {
        if (shape == UiCursorShape.DEFAULT) return 0L // 0 = window's default arrow cursor
        return cursors.getOrPut(shape) { glfwCreateStandardCursor(standardCursor(shape)) }
    }

    private fun standardCursor(shape: UiCursorShape): Int = when (shape) {
        UiCursorShape.DEFAULT -> GLFW_ARROW_CURSOR
        UiCursorShape.HAND -> GLFW_POINTING_HAND_CURSOR
        UiCursorShape.MOVE -> GLFW_RESIZE_ALL_CURSOR
        UiCursorShape.TEXT -> GLFW_IBEAM_CURSOR
        UiCursorShape.CROSSHAIR -> GLFW_CROSSHAIR_CURSOR
        UiCursorShape.RESIZE_HORIZONTAL -> GLFW_RESIZE_EW_CURSOR
        UiCursorShape.RESIZE_VERTICAL -> GLFW_RESIZE_NS_CURSOR
        UiCursorShape.RESIZE_NESW -> GLFW_RESIZE_NESW_CURSOR
        UiCursorShape.RESIZE_NWSE -> GLFW_RESIZE_NWSE_CURSOR
    }
}
