package ru.hollowhorizon.hollowengine.client.ui.ide

import com.sun.jna.Platform
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.WinDef.HWND
import org.lwjgl.glfw.GLFW
import org.lwjgl.glfw.GLFWDropCallback
import org.lwjgl.glfw.GLFWNativeWin32
import org.lwjgl.glfw.GLFWWindowFocusCallback
import ru.hollowhorizon.hollowengine.HollowEngine
import ru.hollowhorizon.hollowengine.client.ui.UiCursorManager
import java.io.File

internal data class HollowIdeExternalFileDrag(val files: List<File>) {
    private val sources = files.map { it.toPath().toAbsolutePath().normalize() }

    fun canImportInto(folder: File): Boolean {
        val target = folder.toPath().toAbsolutePath().normalize()
        return sources.isNotEmpty() && sources.none { target.startsWith(it) }
    }
}

/** Chains the window's file-drop callback and delegates untouched drops back to Minecraft. */
internal class HollowIdeExternalFiles(
    private val onDrop: (List<File>, Float, Float) -> Boolean,
    private val onMove: (List<File>, Float, Float) -> Boolean,
    private val onLeave: () -> Unit,
    private val onFocusLost: () -> Unit,
    private val onFocusGained: () -> Unit,
) {
    private var window = 0L
    private var previous: GLFWDropCallback? = null
    private var callback: GLFWDropCallback? = null
    private var previousFocus: GLFWWindowFocusCallback? = null
    private var focusCallback: GLFWWindowFocusCallback? = null
    private var enabled = false
    private var nativeTarget: WindowsFileDropTarget? = null
    private var nativeAttempted = false
    private val clientWidth = IntArray(1)
    private val clientHeight = IntArray(1)

    val active: Boolean get() = nativeTarget?.active == true

    fun update(window: Long, enabled: Boolean) {
        this.enabled = enabled
        if (this.window != window && callback != null) uninstall()
        this.window = window
        updateNativeTarget()
        if (callback != null || !enabled) return
        var fallback: GLFWDropCallback? = null
        val handler = GLFWDropCallback.create { handle, count, names ->
            val handled = this.enabled && runCatching {
                val files = List(count) { File(GLFWDropCallback.getName(names, it)) }
                val x = DoubleArray(1)
                val y = DoubleArray(1)
                val width = IntArray(1)
                val height = IntArray(1)
                GLFW.glfwGetCursorPos(handle, x, y)
                GLFW.glfwGetWindowSize(handle, width, height)
                onDrop(
                    files,
                    x[0].toFloat() * HollowIdeScale.scaledWidth() / width[0].coerceAtLeast(1),
                    y[0].toFloat() * HollowIdeScale.scaledHeight() / height[0].coerceAtLeast(1),
                )
            }.onFailure { error ->
                HollowEngine.LOGGER.warn("Could not accept files dropped into the IDE", error)
            }.getOrDefault(false)
            if (!handled) fallback?.invoke(handle, count, names)
        }
        previous = GLFW.glfwSetDropCallback(window, handler)
        fallback = previous
        callback = handler
        var focusFallback: GLFWWindowFocusCallback? = null
        val focusHandler = GLFWWindowFocusCallback.create { handle, focused ->
            if (!focused && this.enabled) {
                runCatching(onFocusLost).onFailure { error ->
                    HollowEngine.LOGGER.warn("Could not transfer the active file drag to the desktop", error)
                }
            }
            focusFallback?.invoke(handle, focused)
            if (focused) onFocusGained()
        }
        previousFocus = GLFW.glfwSetWindowFocusCallback(window, focusHandler)
        focusFallback = previousFocus
        focusCallback = focusHandler
    }

    private fun updateNativeTarget() {
        if (!enabled) {
            nativeTarget?.close()
            nativeTarget = null
            nativeAttempted = false
        } else if (Platform.isWindows() && !nativeAttempted) {
            nativeAttempted = true
            nativeTarget = runCatching {
                WindowsFileDropTarget(
                    HWND(Pointer(GLFWNativeWin32.glfwGetWin32Window(window))),
                    onMove = { files, x, y ->
                        UiCursorManager.nativeControl(window, this, true)
                        atClientPoint(files, x, y, onMove)
                    },
                    onLeave = {
                        UiCursorManager.nativeControl(window, this, false)
                        onLeave()
                    },
                    onDrop = { files, x, y -> atClientPoint(files, x, y, onDrop) },
                )
            }.onFailure { HollowEngine.LOGGER.warn("Could not register native file drag feedback", it) }.getOrNull()
        }
    }

    private fun atClientPoint(
        files: List<File>, x: Int, y: Int, action: (List<File>, Float, Float) -> Boolean,
    ): Boolean {
        if (!enabled) return false
        GLFW.glfwGetWindowSize(window, clientWidth, clientHeight)
        return action(
            files,
            x * HollowIdeScale.scaledWidth() / clientWidth[0].coerceAtLeast(1),
            y * HollowIdeScale.scaledHeight() / clientHeight[0].coerceAtLeast(1)
        )
    }

    private fun uninstall() {
        nativeTarget?.close()
        nativeTarget = null
        nativeAttempted = false
        val installed = callback ?: return

        val current = GLFW.glfwSetDropCallback(window, previous)
        if (current?.address() == installed.address()) {
            installed.free()
        } else {
            GLFW.glfwSetDropCallback(window, current)
        }
        callback = null
        val focus = focusCallback ?: return
        val currentFocus = GLFW.glfwSetWindowFocusCallback(window, previousFocus)
        if (currentFocus?.address() == focus.address()) focus.free()
        else GLFW.glfwSetWindowFocusCallback(window, currentFocus)
        focusCallback = null
    }
}
