package ru.hollowhorizon.hollowengine.client.ui

internal enum class UiScrollbarPointerArea {
    THUMB,
    TRACK,
}

internal data class UiScrollbarDragState(
    val nodeKey: String,
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

internal fun DrawScrollbarCommand.pointerAreaAt(mouseX: Float, mouseY: Float): UiScrollbarPointerArea? {
    val local = pointerLocal(mouseX, mouseY) ?: return null
    return when {
        thumb.contains(local.x, local.y) -> UiScrollbarPointerArea.THUMB
        track.contains(local.x, local.y) -> UiScrollbarPointerArea.TRACK
        else -> null
    }
}

internal fun DrawScrollbarCommand.dragStateAt(mouseX: Float, mouseY: Float): UiScrollbarDragState? {
    val inverse = transform.inverse() ?: return null
    val local = inverse.transform(mouseX, mouseY, 0f)
    if (pointerAreaAt(mouseX, mouseY) != UiScrollbarPointerArea.THUMB) return null
    return UiScrollbarDragState(
        nodeKey = UiNodeKeys.key(node),
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

internal fun DrawScrollbarCommand.trackClickOffset(layout: UiLayoutNode, mouseX: Float, mouseY: Float): UiScrollOffset {
    val inverse = transform.inverse() ?: return layout.scrollOffset
    val drag = UiScrollbarDragState(
        nodeKey = UiNodeKeys.key(node),
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

private fun DrawScrollbarCommand.pointerLocal(mouseX: Float, mouseY: Float): UiVec3? {
    return transform.inverse()?.transform(mouseX, mouseY, 0f)
}

internal fun scrollWheelDelta(
    range: UiScrollOffset,
    scrollX: Double,
    scrollY: Double,
    horizontalModifier: Boolean,
): UiScrollOffset {
    val horizontalOnly = scrollX == 0.0 && range.x > 0f && range.y <= 0f
    val emulateHorizontal = (horizontalModifier && scrollX == 0.0) || horizontalOnly
    return UiScrollOffset(
        x = (if (emulateHorizontal) -scrollY else -scrollX).toFloat(),
        y = (if (emulateHorizontal || horizontalModifier) 0.0 else -scrollY).toFloat(),
    )
}
