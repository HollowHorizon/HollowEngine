package ru.hollowhorizon.hollowengine.client.ui.scroll

import ru.hollowhorizon.hollowengine.client.ui.UiMatrix4
import ru.hollowhorizon.hollowengine.client.ui.UiNode
import ru.hollowhorizon.hollowengine.client.ui.layout.UiLayoutNode
import ru.hollowhorizon.hollowengine.client.ui.layout.UiRect
import ru.hollowhorizon.hollowengine.client.ui.scrollHandle

/**
 * A scrollbar press in progress. Both a thumb grab and a track press produce one, so pressing
 * anywhere on the bar jumps to that spot and then keeps following the pointer.
 */
internal data class UiScrollbarDragState(
    val node: UiNode,
    val handle: UiScrollHandle,
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

/** Grabbing the thumb keeps the pointer at the same spot within it. */
internal fun scrollbarThumbDragState(
    layouts: Map<UiNode, UiLayoutNode>,
    thumb: ScrollbarThumbNode,
    mouseX: Float,
    mouseY: Float,
): UiScrollbarDragState? {
    val scrollbar = thumb.layoutState.parentNode as? ScrollbarNode ?: return null
    return scrollbarDragState(layouts, scrollbar, mouseX, mouseY) { local, thumbRect ->
        when (thumb.orientation) {
            ScrollbarOrientation.VERTICAL -> local.y - thumbRect.y
            ScrollbarOrientation.HORIZONTAL -> local.x - thumbRect.x
        }
    }
}

/** Pressing the track centres the thumb on the pointer, then tracks it like a thumb drag would. */
internal fun scrollbarTrackDragState(
    layouts: Map<UiNode, UiLayoutNode>,
    scrollbar: ScrollbarNode,
    mouseX: Float,
    mouseY: Float,
): UiScrollbarDragState? = scrollbarDragState(layouts, scrollbar, mouseX, mouseY) { _, thumbRect ->
    when (scrollbar.orientation) {
        ScrollbarOrientation.VERTICAL -> thumbRect.height * 0.5f
        ScrollbarOrientation.HORIZONTAL -> thumbRect.width * 0.5f
    }
}

private inline fun scrollbarDragState(
    layouts: Map<UiNode, UiLayoutNode>,
    scrollbar: ScrollbarNode,
    mouseX: Float,
    mouseY: Float,
    grabOffset: (local: ru.hollowhorizon.hollowengine.client.ui.UiVec3, thumb: UiRect) -> Float,
): UiScrollbarDragState? {
    val container = scrollbar.layoutState.parentNode ?: return null
    val handle = container.scrollHandle() ?: return null
    val containerLayout = layouts[container] ?: return null
    val trackLayout = layouts[scrollbar] ?: return null
    val thumbLayout = layouts[scrollbar.thumb] ?: return null
    val inverse = containerLayout.inputTransform.inverse() ?: return null
    val trackRel = trackLayout.rect.relativeTo(containerLayout.rect)
    val thumbRel = thumbLayout.rect.relativeTo(containerLayout.rect)
    val local = inverse.transform(mouseX, mouseY, 0f)
    return UiScrollbarDragState(
        node = container,
        handle = handle,
        orientation = scrollbar.orientation,
        track = trackRel,
        thumb = thumbRel,
        inverseTransform = inverse,
        grabOffset = grabOffset(local, thumbRel),
    )
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
