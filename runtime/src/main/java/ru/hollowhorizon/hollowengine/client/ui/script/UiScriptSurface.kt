package ru.hollowhorizon.hollowengine.client.ui.script

import net.minecraft.client.Minecraft
import net.minecraft.nbt.CompoundTag
import org.lwjgl.glfw.GLFW
import ru.hollowhorizon.hollowengine.client.ui.HollowUiSurface
import ru.hollowhorizon.hollowengine.client.ui.LocalUiFrameTimeNanos
import ru.hollowhorizon.hollowengine.client.ui.UiCursorShape
import ru.hollowhorizon.hollowengine.client.ui.currentUiKeyModifiers
import ru.hollowhorizon.hollowengine.client.ui.render.MinecraftUiRenderer
import ru.hollowhorizon.hollowengine.common.ui.UiContent
import ru.hollowhorizon.hollowengine.common.ui.UiData
import ru.hollowhorizon.hollowengine.common.ui.UiScope

/**
 * A composed piece of scripted UI drawn over the HUD. Each overlay owns its own surface so one
 * overlay recomposing never drags the others through a frame build with it.
 */
class UiScriptSurface(
    content: UiContent,
    override val data: UiData,
    override val sessionId: Int?,
    private val interactive: Boolean,
    private val rebuildEveryFrame: Boolean,
    private val onSend: ((CompoundTag) -> Unit)?,
    private val onClose: () -> Unit,
) : UiScope {
    private val surface = HollowUiSurface()
    private val renderer = MinecraftUiRenderer()

    private var pointerX = -1f
    private var pointerY = -1f
    private var activeButton: Int? = null

    init {
        surface.setContent {
            if (rebuildEveryFrame) LocalUiFrameTimeNanos.current
            content(this@UiScriptSurface)
        }
    }

    override fun send(payload: CompoundTag) {
        onSend?.invoke(payload)
    }

    override fun close() = onClose()

    val hasFocusedInput: Boolean get() = interactive && surface.runtime.isAnyFocused

    val cursor: UiCursorShape get() = surface.runtime.cursor

    fun render(nowNanos: Long) {
        val window = Minecraft.getInstance().window
        val width = window.guiScaledWidth.toFloat()
        val height = window.guiScaledHeight.toFloat()
        if (width <= 0f || height <= 0f) return
        if (rebuildEveryFrame) surface.advanceFrameTime(nowNanos)
        val (px, py) = if (interactive) currentPointer(width, height) else (-1f to -1f)
        renderer.render(surface.frame(width, height, px, py, nowNanos))
    }

    fun mouseMoved(x: Float, y: Float): Boolean {
        if (!interactive) return false
        val dx = x - pointerX.coerceAtLeast(0f)
        val dy = y - pointerY.coerceAtLeast(0f)
        pointerX = x
        pointerY = y
        val button = activeButton ?: return false
        return surface.runtime.mouseDragged(x, y, button, dx, dy, currentUiKeyModifiers())
    }

    fun mousePressed(x: Float, y: Float, button: Int, action: Int): Boolean {
        if (!interactive) return false
        pointerX = x
        pointerY = y
        return when (action) {
            GLFW.GLFW_PRESS -> surface.runtime.mouseClicked(x, y, button, currentUiKeyModifiers())
                .also { if (it) activeButton = button }

            GLFW.GLFW_RELEASE -> {
                activeButton = null
                surface.runtime.mouseReleased(x, y, button, currentUiKeyModifiers())
            }

            else -> false
        }
    }

    fun mouseScrolled(x: Float, y: Float, scrollX: Double, scrollY: Double): Boolean {
        if (!interactive) return false
        return surface.runtime.mouseScrolled(x, y, scrollX.toFloat(), scrollY.toFloat(), currentUiKeyModifiers())
    }

    fun keyPressed(key: Int, scanCode: Int, action: Int, modifiers: Int): Boolean {
        if (!interactive) return false
        if (action != GLFW.GLFW_PRESS && action != GLFW.GLFW_REPEAT) return false
        return surface.runtime.keyPressed(key, scanCode, modifiers, repeat = action == GLFW.GLFW_REPEAT)
    }

    fun charTyped(codePoint: Int, modifiers: Int): Boolean {
        if (!interactive) return false
        return surface.runtime.charTyped(codePoint.toChar(), modifiers)
    }

    /** Releases GPU and composition resources; call when the overlay stops being shown. */
    fun dispose() {
        renderer.close()
        surface.close()
    }

    private fun currentPointer(width: Float, height: Float): Pair<Float, Float> {
        if (pointerX >= 0f) return pointerX to pointerY
        return width / 2f to height / 2f
    }
}
