package ru.hollowhorizon.hollowengine.client.gui.scripting

import net.minecraft.client.Minecraft
import org.lwjgl.glfw.GLFW
import ru.hollowhorizon.hollowengine.client.ui.PopupNode
import ru.hollowhorizon.hollowengine.client.ui.UiNode
import ru.hollowhorizon.hollowengine.client.ui.UiPopupAnchor

internal data class HollowIdeOverlayPoint(val x: Float, val y: Float)

internal fun hollowIdeOverlayPoint(x: Float, y: Float): HollowIdeOverlayPoint {
    val minecraft = Minecraft.getInstance()
    val window = minecraft.window
    val sourceWidth = minecraft.mainRenderTarget.width.takeIf { it > 0 }?.toFloat() ?: window.width.toFloat()
    val sourceHeight = minecraft.mainRenderTarget.height.takeIf { it > 0 }?.toFloat() ?: window.height.toFloat()
    return HollowIdeOverlayPoint(
        x = x * window.guiScaledWidth.toFloat() / sourceWidth,
        y = y * window.guiScaledHeight.toFloat() / sourceHeight,
    )
}

internal fun hollowIdeHorizontalScrollModifierDown(): Boolean {
    val window = Minecraft.getInstance().window.window
    return GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS ||
            GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS ||
            hollowIdeControlModifierDown(window)
}

internal fun hollowIdeControlModifierDown(window: Long = Minecraft.getInstance().window.window): Boolean {
    return GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS ||
            GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS
}

internal fun hollowIdeModifierMask(window: Long = Minecraft.getInstance().window.window): Int {
    var modifiers = 0
    if (hollowIdeControlModifierDown(window)) modifiers = modifiers or GLFW.GLFW_MOD_CONTROL
    if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS ||
        GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS
    ) {
        modifiers = modifiers or GLFW.GLFW_MOD_SHIFT
    }
    if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_ALT) == GLFW.GLFW_PRESS ||
        GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_ALT) == GLFW.GLFW_PRESS
    ) {
        modifiers = modifiers or GLFW.GLFW_MOD_ALT
    }
    return modifiers
}

internal fun UiNode.hasLiveCursorPopup(): Boolean {
    return children.any { child ->
        (child is PopupNode && child.anchor.isLiveCursor()) || child.hasLiveCursorPopup()
    }
}

private fun UiPopupAnchor.isLiveCursor(): Boolean {
    return this is UiPopupAnchor.Cursor && (!x.isFinite() || !y.isFinite())
}
