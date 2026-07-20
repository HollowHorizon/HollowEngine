package ru.hollowhorizon.hollowengine.client.ui.scroll

import ru.hollowhorizon.hollowengine.client.ui.BaseUiNode
import ru.hollowhorizon.hollowengine.client.ui.UiColor
import ru.hollowhorizon.hollowengine.client.ui.UiNode
import ru.hollowhorizon.hollowengine.client.ui.style.*

const val UiScrollbarType = "scrollbar"
const val UiScrollbarThumbType = "scrollbar-thumb"

/**
 * A scrollbar rendered as a node (the track) with a single thumb child. These
 * nodes are NOT part of the Compose tree - the framework synthesizes one per scrollable
 * container (cached for stable identity) so every scrollable widget gets a scrollbar without
 * each composable having to add one. Their track/thumb geometry is set by the layout scroll
 * post-pass from the container's scroll range/offset; they draw + hit-test like any node.
 */
class ScrollbarNode(
    val orientation: ScrollbarOrientation,
) : BaseUiNode(UiScrollbarType, id = null, tags = listOf(orientation.tagName)) {
    val thumb: ScrollbarThumbNode = ScrollbarThumbNode(orientation)

    init {
        children.add(thumb)
        thumb.layoutState.attachTo(this)
        resolvedSnapshot = ScrollbarDefaultStyles.track
        thumb.resolvedSnapshot = ScrollbarDefaultStyles.thumb
    }
}

class ScrollbarThumbNode(
    val orientation: ScrollbarOrientation,
) : BaseUiNode(UiScrollbarThumbType, id = null, tags = listOf(orientation.tagName))

internal val ScrollbarOrientation.tagName: String
    get() = when (this) {
        ScrollbarOrientation.VERTICAL -> "vertical"
        ScrollbarOrientation.HORIZONTAL -> "horizontal"
    }

/** The scroll container a scrollbar (or its thumb) belongs to. */
internal fun UiNode.scrollbarContainer(): UiNode? = when (this) {
    is ScrollbarNode -> layoutState.parentNode
    is ScrollbarThumbNode -> (layoutState.parentNode as? ScrollbarNode)?.layoutState?.parentNode
    else -> null
}

internal object ScrollbarDefaultStyles {
    val track: UiComputedStyle = UiStylePatch().apply {
        background = UiPaint.Color(UiColor(0f, 0f, 0f, 0.42f))
        borderRadius = 3.5f
    }.resolve()

    val thumb: UiComputedStyle = UiStylePatch().apply {
        background = UiPaint.Color(UiColor(0.78f, 0.84f, 0.94f, 0.9f))
        borderRadius = 3.5f
        clickable = true
        draggable = true
        hoverable = true
    }.resolve()
}
