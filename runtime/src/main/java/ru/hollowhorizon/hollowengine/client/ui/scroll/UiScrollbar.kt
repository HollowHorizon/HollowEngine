package ru.hollowhorizon.hollowengine.client.ui.scroll

import ru.hollowhorizon.hollowengine.client.ui.BaseUiNode
import ru.hollowhorizon.hollowengine.client.ui.UiColor
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

    private var appliedTrack: UiScrollbarPartStyle? = null
    private var appliedThumb: UiScrollbarPartStyle? = null

    init {
        children.add(thumb)
        thumb.layoutState.attachTo(this)
        resolvedSnapshot = ScrollbarDefaultStyles.track
        thumb.resolvedSnapshot = ScrollbarDefaultStyles.thumb
    }

    /** Re-resolves the two part styles, skipping the work while the container's styling is unchanged. */
    internal fun applyPartStyles(style: UiScrollbarStyle) {
        if (appliedTrack != style.track) {
            appliedTrack = style.track
            resolvedSnapshot = ScrollbarDefaultStyles.resolvePart(style.track, ScrollbarDefaultStyles.TrackPaint, false)
        }
        if (appliedThumb != style.thumb) {
            appliedThumb = style.thumb
            thumb.resolvedSnapshot =
                ScrollbarDefaultStyles.resolvePart(style.thumb, ScrollbarDefaultStyles.ThumbPaint, true)
        }
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

internal object ScrollbarDefaultStyles {
    val TrackPaint: UiPaint = UiPaint.Color(UiColor(0f, 0f, 0f, 0.42f))
    val ThumbPaint: UiPaint = UiPaint.Color(UiColor(0.78f, 0.84f, 0.94f, 0.9f))
    private const val DefaultRadius = 3.5f

    val track: UiComputedStyle = resolvePart(UiScrollbarPartStyle(), TrackPaint, draggable = false)
    val thumb: UiComputedStyle = resolvePart(UiScrollbarPartStyle(), ThumbPaint, draggable = true)

    fun resolvePart(part: UiScrollbarPartStyle, fallbackPaint: UiPaint, draggable: Boolean): UiComputedStyle =
        UiStylePatch().apply {
            background = part.paint ?: fallbackPaint
            borderRadius = part.radius ?: DefaultRadius
            part.border?.let { border = it }
            part.fit?.let { imageFit = it }
            part.slice?.let { imageSlice = it }
            clickable = true
            hoverable = true
            if (draggable) this.draggable = true
        }.resolve()
}
