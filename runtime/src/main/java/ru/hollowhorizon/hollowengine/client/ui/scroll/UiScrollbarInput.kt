package ru.hollowhorizon.hollowengine.client.ui.scroll

import ru.hollowhorizon.hollowengine.client.ui.UiMatrix4
import ru.hollowhorizon.hollowengine.client.ui.UiNode
import ru.hollowhorizon.hollowengine.client.ui.layout.UiLayoutNode
import ru.hollowhorizon.hollowengine.client.ui.layout.UiRect

internal enum class UiScrollbarPointerArea {
    THUMB,
    TRACK,
}

internal data class UiScrollbarHandle(
    val node: UiNode,
    val geometry: UiScrollbarGeometry,
    val transform: UiMatrix4,
) {
    val track: UiRect get() = geometry.track
    val thumb: UiRect get() = geometry.thumb
    val orientation: ScrollbarOrientation get() = geometry.orientation
}

internal fun UiScrollbarHandle.pointerAreaAt(mouseX: Float, mouseY: Float): UiScrollbarPointerArea? {
    val local = pointerLocal(mouseX, mouseY) ?: return null
    return when {
        thumb.contains(local.x, local.y) -> UiScrollbarPointerArea.THUMB
        track.contains(local.x, local.y) -> UiScrollbarPointerArea.TRACK
        else -> null
    }
}

internal data class UiScrollbarDragState(
    val node: UiNode,
    val orientation: ScrollbarOrientation,
    val track: UiRect,
    val thumb: UiRect,
    val inverseTransform: UiMatrix4,
    val grabOffset: Float,
) {
    fun offsetFor(layout: UiLayoutNode, mouseX: Float, mouseY: Float): UiScrollOffset {
        val local = inverseTransform.transform(mouseX, mouseY, 0f)
        return when (orientation) {
            ScrollbarOrientation.VERTICAL -> {
                val movable = (track.height - thumb.height).coerceAtLeast(1f)
                val progress = ((local.y - track.y - grabOffset) / movable).coerceIn(0f, 1f)
                layout.scrollOffset.copy(y = progress * layout.scrollRange.y)
            }

            ScrollbarOrientation.HORIZONTAL -> {
                val movable = (track.width - thumb.width).coerceAtLeast(1f)
                val progress = ((local.x - track.x - grabOffset) / movable).coerceIn(0f, 1f)
                layout.scrollOffset.copy(x = progress * layout.scrollRange.x)
            }
        }
    }
}

internal fun UiScrollbarHandle.dragStateAt(mouseX: Float, mouseY: Float): UiScrollbarDragState? {
    val inverse = transform.inverse() ?: return null
    val local = inverse.transform(mouseX, mouseY, 0f)
    if (pointerAreaAt(mouseX, mouseY) != UiScrollbarPointerArea.THUMB) return null
    return UiScrollbarDragState(
        node = node,
        orientation = orientation,
        track = track,
        thumb = thumb,
        inverseTransform = inverse,
        grabOffset = when (orientation) {
            ScrollbarOrientation.VERTICAL -> local.y - thumb.y
            ScrollbarOrientation.HORIZONTAL -> local.x - thumb.x
        },
    )
}

internal fun UiScrollbarHandle.trackClickOffset(layout: UiLayoutNode, mouseX: Float, mouseY: Float): UiScrollOffset {
    val inverse = transform.inverse() ?: return layout.scrollOffset
    val drag = UiScrollbarDragState(
        node = node,
        orientation = orientation,
        track = track,
        thumb = thumb,
        inverseTransform = inverse,
        grabOffset = when (orientation) {
            ScrollbarOrientation.VERTICAL -> thumb.height * 0.5f
            ScrollbarOrientation.HORIZONTAL -> thumb.width * 0.5f
        },
    )
    return drag.offsetFor(layout, mouseX, mouseY)
}

private fun UiScrollbarHandle.pointerLocal(
    mouseX: Float,
    mouseY: Float,
): ru.hollowhorizon.hollowengine.client.ui.UiVec3? {
    return transform.inverse()?.transform(mouseX, mouseY, 0f)
}

internal fun scrollWheelDelta(
    range: UiScrollOffset,
    scrollX: Float,
    scrollY: Float,
    horizontalModifier: Boolean,
): UiScrollOffset {
    val horizontalOnly = scrollX == 0f && range.x > 0f && range.y <= 0f
    val emulateHorizontal = horizontalModifier && scrollX == 0f || horizontalOnly
    return UiScrollOffset(
        x = if (emulateHorizontal) -scrollY else -scrollX,
        y = if (emulateHorizontal || horizontalModifier) 0f else -scrollY,
    )
}
