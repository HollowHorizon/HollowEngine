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

    /**
     * Under the pointer or being dragged, set by the input controller.
     */
    internal var engaged: Boolean = false
        set(value) {
            if (field == value) return
            val now = System.nanoTime()
            progressAtChange = hoverProgress(now)
            changedAtNanos = now
            field = value
            layoutState.invalidateLayout()
        }

    private var progressAtChange = 0f
    private var changedAtNanos = 0L

    /** How far into its hover look the scrollbar is at [nowNanos], eased, 0 at rest and 1 engaged. */
    internal fun hoverProgress(nowNanos: Long): Float {
        val target = if (engaged) 1f else 0f
        val t = ((nowNanos - changedAtNanos) / HoverTransitionNanos).coerceIn(0f, 1f)
        val eased = 1f - (1f - t) * (1f - t)
        return progressAtChange + (target - progressAtChange) * eased
    }

    /** Whether [hoverProgress] is still on its way, so the next frame has to lay the bar out again. */
    internal fun isEasing(nowNanos: Long): Boolean = nowNanos - changedAtNanos < HoverTransitionNanos

    private var appliedTrack: UiScrollbarPartStyle? = null
    private var appliedThumb: UiScrollbarPartStyle? = null

    init {
        children.add(thumb)
        thumb.layoutState.attachTo(this)
        resolvedSnapshot = ScrollbarDefaultStyles.track
        thumb.resolvedSnapshot = ScrollbarDefaultStyles.thumb
    }

    /** Re-resolves the two part styles, skipping the work while the container's styling is unchanged. */
    internal fun applyPartStyles(style: UiScrollbarStyle, hover: Float) {
        if (appliedTrack != style.track) {
            appliedTrack = style.track
            resolvedSnapshot = ScrollbarDefaultStyles.resolvePart(style.track, ScrollbarDefaultStyles.TrackPaint, false)
        }
        val hoverPaint = style.thumbHover.paint
        val thumbPart = when {
            hover <= 0f || hoverPaint == null -> style.thumb
            hover >= 1f -> style.thumb.merge(style.thumbHover)
            else -> style.thumb.copy(
                paint = interpolatePaint(style.thumb.paint ?: ScrollbarDefaultStyles.ThumbPaint, hoverPaint, hover),
            )
        }
        if (appliedThumb != thumbPart) {
            appliedThumb = thumbPart
            thumb.resolvedSnapshot =
                ScrollbarDefaultStyles.resolvePart(thumbPart, ScrollbarDefaultStyles.ThumbPaint, true)
        }
    }
}

private const val HoverTransitionNanos = 90_000_000f

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
