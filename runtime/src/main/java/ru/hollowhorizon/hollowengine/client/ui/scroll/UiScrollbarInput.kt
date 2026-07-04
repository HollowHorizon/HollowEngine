package ru.hollowhorizon.hollowengine.client.ui.scroll

import ru.hollowhorizon.hollowengine.client.ui.UiMatrix4
import ru.hollowhorizon.hollowengine.client.ui.UiNode
import ru.hollowhorizon.hollowengine.client.ui.layout.UiLayoutNode
import ru.hollowhorizon.hollowengine.client.ui.layout.UiRect

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

private fun UiRect.relativeTo(other: UiRect) = UiRect(x - other.x, y - other.y, width, height)

/** Builds a drag state for pressing a thumb node (grab keeps the cursor offset within it). */
internal fun scrollbarThumbDragState(
    layouts: Map<UiNode, UiLayoutNode>,
    thumb: ScrollbarThumbNode,
    mouseX: Float,
    mouseY: Float,
): UiScrollbarDragState? {
    val scrollbar = thumb.layoutState.parentNode as? ScrollbarNode ?: return null
    val container = scrollbar.layoutState.parentNode ?: return null
    val containerLayout = layouts[container] ?: return null
    val trackLayout = layouts[scrollbar] ?: return null
    val thumbLayout = layouts[thumb] ?: return null
    val inverse = containerLayout.inputTransform.inverse() ?: return null
    val trackRel = trackLayout.rect.relativeTo(containerLayout.rect)
    val thumbRel = thumbLayout.rect.relativeTo(containerLayout.rect)
    val local = inverse.transform(mouseX, mouseY, 0f)
    val grab = when (thumb.orientation) {
        ScrollbarOrientation.VERTICAL -> local.y - thumbRel.y
        ScrollbarOrientation.HORIZONTAL -> local.x - thumbRel.x
    }
    return UiScrollbarDragState(container, thumb.orientation, trackRel, thumbRel, inverse, grab)
}

internal fun scrollbarTrackJumpOffset(
    layouts: Map<UiNode, UiLayoutNode>,
    scrollbar: ScrollbarNode,
    mouseX: Float,
    mouseY: Float,
): Pair<UiNode, UiScrollOffset>? {
    val container = scrollbar.layoutState.parentNode ?: return null
    val containerLayout = layouts[container] ?: return null
    val trackLayout = layouts[scrollbar] ?: return null
    val thumbLayout = scrollbar.thumb?.let { layouts[it] } ?: return null
    val inverse = containerLayout.inputTransform.inverse() ?: return null
    val trackRel = trackLayout.rect.relativeTo(containerLayout.rect)
    val thumbRel = thumbLayout.rect.relativeTo(containerLayout.rect)
    val grab = when (scrollbar.orientation) {
        ScrollbarOrientation.VERTICAL -> thumbRel.height * 0.5f
        ScrollbarOrientation.HORIZONTAL -> thumbRel.width * 0.5f
    }
    val drag = UiScrollbarDragState(container, scrollbar.orientation, trackRel, thumbRel, inverse, grab)
    return container to drag.offsetFor(containerLayout, mouseX, mouseY)
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
