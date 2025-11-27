package ru.hollowhorizon.hollowengine.client.kool.window

import de.fabmax.kool.input.CursorMode
import de.fabmax.kool.input.CursorShape
import de.fabmax.kool.input.PlatformInput
import de.fabmax.kool.util.BackendScope
import kotlinx.coroutines.launch
import org.lwjgl.glfw.GLFW.*
import ru.hollowhorizon.hollowengine.client.kool.KoolManager


class MCInput : PlatformInput {
    private val cursorShapes = mutableMapOf<CursorShape, Long>()
    init {
        createStandardCursors()
    }
    private var currentCursorShape = CursorShape.DEFAULT

    val window get() = KoolManager.context.window
    override fun setCursorMode(cursorMode: CursorMode) {
        BackendScope.launch {
            if (cursorMode == CursorMode.NORMAL || window.flags.isFocused) {
                val x = doubleArrayOf(0.0)
                val y = doubleArrayOf(0.0)
                val windowHandle = window.windowHandle
                glfwGetCursorPos(windowHandle, x, y)
                glfwSetInputMode(windowHandle, GLFW_CURSOR, cursorMode.glfwMode)
                if (cursorMode == CursorMode.NORMAL) {
                    val setX = ((x[0] % window.size.x) + window.size.x) % window.size.x
                    val setY = ((y[0] % window.size.y) + window.size.y) % window.size.y
                    glfwSetCursorPos(windowHandle, setX, setY)
                }
            }
        }
    }

    override fun applyCursorShape(cursorShape: CursorShape) {
        BackendScope.launch {
            if (cursorShape != currentCursorShape) {
                glfwSetCursor(window.windowHandle, cursorShapes[cursorShape] ?: 0L)
                currentCursorShape = cursorShape
            }
        }
    }

    private fun createStandardCursors() {
        cursorShapes[CursorShape.DEFAULT] = 0
        cursorShapes[CursorShape.TEXT] = glfwCreateStandardCursor(GLFW_IBEAM_CURSOR)
        cursorShapes[CursorShape.CROSSHAIR] = glfwCreateStandardCursor(GLFW_CROSSHAIR_CURSOR)
        cursorShapes[CursorShape.HAND] = glfwCreateStandardCursor(GLFW_HAND_CURSOR)
        cursorShapes[CursorShape.RESIZE_E] = glfwCreateStandardCursor(GLFW_RESIZE_EW_CURSOR)
        cursorShapes[CursorShape.RESIZE_W] = glfwCreateStandardCursor(GLFW_RESIZE_EW_CURSOR)
        cursorShapes[CursorShape.RESIZE_N] = glfwCreateStandardCursor(GLFW_RESIZE_NS_CURSOR)
        cursorShapes[CursorShape.RESIZE_S] = glfwCreateStandardCursor(GLFW_RESIZE_NS_CURSOR)
        cursorShapes[CursorShape.MOVE] = glfwCreateStandardCursor(GLFW_RESIZE_ALL_CURSOR)
    }

    private val CursorMode.glfwMode: Int
        get() = when (this) {
            CursorMode.NORMAL -> GLFW_CURSOR_NORMAL
            CursorMode.LOCKED -> GLFW_CURSOR_DISABLED
        }
}