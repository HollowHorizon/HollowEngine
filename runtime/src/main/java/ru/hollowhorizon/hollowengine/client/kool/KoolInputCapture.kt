package ru.hollowhorizon.hollowengine.client.kool

import de.fabmax.kool.math.Vec2f
import de.fabmax.kool.modules.ui2.UiNode
import de.fabmax.kool.modules.ui2.UiSurface
import de.fabmax.kool.scene.Node
import ru.hollowhorizon.hollowengine.client.ui.ide.HollowIdeOverlay

fun isKoolPointerInputCaptured(x: Float, y: Float): Boolean {
    val position = Vec2f(x, y)
    if (HollowIdeOverlay.isMouseOver(x, y)) return true

    return KoolManager.context.scenes.any { scene ->
        scene.isVisible && scene.isNodeCapturingPointerInput(position)
    }
}

private fun Node.isNodeCapturingPointerInput(position: Vec2f): Boolean {
    if (!isVisible) return false

    if (this is UiSurface && capturesPointerInputAt(position)) {
        return true
    }

    return children.any { it.isNodeCapturingPointerInput(position) }
}

private fun UiSurface.capturesPointerInputAt(position: Vec2f): Boolean {
    if (inputMode == UiSurface.InputCaptureMode.CaptureDisabled) return false
    if (inputHandler.blockAllPointerInput) return true
    if (inputMode == UiSurface.InputCaptureMode.CapturePassthrough) return false

    return viewport.children.any { it.isBlockingNodeAt(position) }
}

private fun UiNode.isBlockingNodeAt(position: Vec2f): Boolean {
    if (!isInClipBounds(position)) return false
    if (modifier.isBlocking && isInBounds(position)) return true
    return children.any { it.isBlockingNodeAt(position) }
}
