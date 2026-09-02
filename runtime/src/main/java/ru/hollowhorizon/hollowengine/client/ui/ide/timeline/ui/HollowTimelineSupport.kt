package ru.hollowhorizon.hollowengine.client.ui.ide.timeline.ui

import org.lwjgl.glfw.GLFW
import ru.hollowhorizon.hollowengine.client.ui.UiColor
import ru.hollowhorizon.hollowengine.client.ui.ide.timeline.AnimLayer
import ru.hollowhorizon.hollowengine.client.ui.ide.timeline.AnimProperty
import ru.hollowhorizon.hollowengine.client.ui.ide.timeline.ChannelCurve
import ru.hollowhorizon.hollowengine.client.ui.ide.timeline.Keyframe
import ru.hollowhorizon.hollowengine.client.ui.ide.timeline.TimelineController
import ru.hollowhorizon.hollowengine.client.ui.ide.timeline.TrackGroup
import ru.hollowhorizon.hollowengine.client.ui.layout.UiRect
import ru.hollowhorizon.hollowengine.client.ui.scroll.UiScrollHandle
import ru.hollowhorizon.hollowengine.client.ui.shape.GenericShape
import ru.hollowhorizon.hollowengine.client.ui.shape.Shape
import ru.hollowhorizon.hollowengine.common.utils.Color
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.round

internal const val TimelineRulerHeight = 28f
internal const val TimelineGroupRowHeight = 22f
internal const val TimelinePropertyRowHeight = 24f
internal const val TimelineLayerRowHeight = 22f
internal const val TimelineChannelRowHeight = 20f
internal const val TimelineLeftPadding = 58f
internal const val CurveValueGutter = 46f
internal const val TimelineMinContentWidth = 600f
internal const val TimelineMaxZoom = 500f
internal const val TimelineMinZoom = 10f
internal const val TimelineMinHeaderWidth = 150f
internal const val TimelineMaxHeaderWidth = 480f
private const val TimelineAutoPanEdge = 48f
internal const val TimelineScrollbarClearance = 12f
internal const val TimelineCullMargin = 120f

internal val TimelineDiamondShape: Shape = GenericShape { size ->
    moveTo(size.width * 0.5f, 0f)
    lineTo(size.width, size.height * 0.5f)
    lineTo(size.width * 0.5f, size.height)
    lineTo(0f, size.height * 0.5f)
    close()
}

internal val PlayheadHeadShape: Shape = GenericShape { size ->
    moveTo(0f, 0f)
    lineTo(size.width, 0f)
    lineTo(size.width, size.height * 0.55f)
    lineTo(size.width * 0.5f, size.height)
    lineTo(0f, size.height * 0.55f)
    close()
}

internal enum class TimelineRowKind {
    GROUP,
    PROPERTY,
    LAYER,
    CHANNEL,
}

internal data class TimelineRow(
    val id: String,
    val label: String,
    val depth: Int,
    val y: Float,
    val height: Float,
    val kind: TimelineRowKind,
    val group: TrackGroup? = null,
    val property: AnimProperty<*>? = null,
    val layer: AnimLayer? = null,
    val curve: ChannelCurve? = null,
    val locked: Boolean = false,
    val visible: Boolean = true,
    val color: Color? = null,
) {
    val curves: List<ChannelCurve>
        get() = when {
            curve != null -> listOf(curve)
            layer != null -> layer.channels
            property != null -> property.layers.flatMap { it.channels }
            group != null -> group.allProperties().flatMap { owner -> owner.layers.flatMap { it.channels } }
            else -> emptyList()
        }
}

internal fun timelineRows(controller: TimelineController): List<TimelineRow> {
    val rows = mutableListOf<TimelineRow>()
    var y = TimelineRulerHeight

    fun appendGroup(group: TrackGroup, depth: Int, parentLocked: Boolean, parentVisible: Boolean) {
        val locked = parentLocked || group.isLocked
        val visible = parentVisible && group.isVisible
        rows += TimelineRow(
            id = "timeline-group-${System.identityHashCode(group)}",
            label = group.nameState,
            depth = depth,
            y = y,
            height = TimelineGroupRowHeight,
            kind = TimelineRowKind.GROUP,
            group = group,
            locked = locked,
            visible = visible,
        )
        y += TimelineGroupRowHeight
        if (group.isCollapsed) return

        group.children.forEach { appendGroup(it, depth + 1, locked, visible) }
        group.properties.forEach { property ->
            rows += TimelineRow(
                id = "timeline-property-${System.identityHashCode(property)}",
                label = property.nameState,
                depth = depth + 1,
                y = y,
                height = TimelinePropertyRowHeight,
                kind = TimelineRowKind.PROPERTY,
                property = property,
                locked = locked,
                visible = visible && property.layers.any { it.isVisible },
            )
            y += TimelinePropertyRowHeight
            if (!property.isExpanded) return@forEach

            property.layers.forEach { layer ->
                val layerVisible = visible && layer.isVisible
                rows += TimelineRow(
                    id = "timeline-layer-${System.identityHashCode(layer)}",
                    label = layer.nameState,
                    depth = depth + 2,
                    y = y,
                    height = TimelineLayerRowHeight,
                    kind = TimelineRowKind.LAYER,
                    property = property,
                    layer = layer,
                    locked = locked || layer.isLocked,
                    visible = layerVisible,
                )
                y += TimelineLayerRowHeight
                if (!layer.isExpanded) return@forEach

                layer.channels.forEach { curve ->
                    rows += TimelineRow(
                        id = "timeline-channel-${System.identityHashCode(curve)}",
                        label = curve.name,
                        depth = depth + 3,
                        y = y,
                        height = TimelineChannelRowHeight,
                        kind = TimelineRowKind.CHANNEL,
                        property = property,
                        layer = layer,
                        curve = curve,
                        locked = locked || layer.isLocked,
                        visible = layerVisible && curve.isVisible,
                        color = curve.color,
                    )
                    y += TimelineChannelRowHeight
                }
            }
        }
    }

    controller.groups.forEach { appendGroup(it, 0, parentLocked = false, parentVisible = true) }
    return rows
}

internal fun visibleKeys(row: TimelineRow, from: Float, to: Float): List<Keyframe> {
    val curve = row.curve ?: return emptyList()
    return curve.keyframes.filter { it.time in from..to }
}

internal fun timelineContentWidth(
    workAreaEnd: Float,
    maxKeyTime: Float,
    pixelsPerSecond: Float,
    viewportWidth: Float,
): Float {
    val endWidth = (workAreaEnd + 5f) * pixelsPerSecond + TimelineLeftPadding
    val keyWidth = (maxKeyTime + 5f) * pixelsPerSecond + TimelineLeftPadding
    return max(TimelineMinContentWidth, max(max(endWidth, keyWidth), viewportWidth))
}

internal fun timelineTimeAt(localX: Float, pixelsPerSecond: Float, workAreaEnd: Float, modifiers: Int = 0): Float =
    snapTimelineTime((localX - TimelineLeftPadding) / pixelsPerSecond, modifiers).coerceIn(0f, workAreaEnd)

internal fun snapTimelineTime(time: Float, modifiers: Int): Float {
    val step = when {
        modifiers and GLFW.GLFW_MOD_ALT != 0 -> 1f
        modifiers and GLFW.GLFW_MOD_SHIFT != 0 -> 0.5f
        else -> return time
    }
    return round(time / step) * step
}

internal fun timelineTimeDelta(deltaX: Float, pixelsPerSecond: Float): Float {
    return deltaX / pixelsPerSecond.coerceAtLeast(1f)
}

internal fun visibleTimeRange(scrollX: Float, viewportWidth: Float, pixelsPerSecond: Float): ClosedFloatingPointRange<Float> {
    if (viewportWidth <= 0f) return -Float.MAX_VALUE..Float.MAX_VALUE
    val start = (scrollX - TimelineCullMargin - TimelineLeftPadding) / pixelsPerSecond
    val end = (scrollX + viewportWidth + TimelineCullMargin - TimelineLeftPadding) / pixelsPerSecond
    return start..max(start, end)
}

internal fun curveValueToY(value: Float, center: Float, span: Float, height: Float): Float {
    if (span <= 0f || height <= 0f) return height * 0.5f
    return height * 0.5f - (value - center) * (height / span)
}

internal fun curveYToValue(y: Float, center: Float, span: Float, height: Float): Float {
    if (span <= 0f || height <= 0f) return center
    return center + (height * 0.5f - y) * (span / height)
}

internal fun curvePixelsPerValue(span: Float, height: Float): Float {
    if (span <= 0f) return 1f
    return height / span
}

internal fun curveValueStep(span: Float): Float {
    if (span <= 0f) return 1f
    val rough = span / 6f
    val magnitude = 10f.pow(floor(log10(rough)))
    val normalized = rough / magnitude
    val step = when {
        normalized < 1.5f -> 1f
        normalized < 3.5f -> 2f
        normalized < 7.5f -> 5f
        else -> 10f
    }
    return step * magnitude
}

internal fun autoPanToContentX(scroll: UiScrollHandle, viewport: UiRect, contentX: Float) {
    if (viewport.width <= 0f) return
    val edge = min(TimelineAutoPanEdge, viewport.width * 0.25f)
    val visibleStart = scroll.offsetX
    val visibleEnd = visibleStart + viewport.width
    val target = when {
        contentX < visibleStart + edge -> contentX - edge
        contentX > visibleEnd - edge -> contentX - viewport.width + edge
        else -> return
    }
    if (abs(target - scroll.offsetX) > 0.5f) scroll.scrollTo(x = target.coerceAtLeast(0f))
}

internal fun timelineRulerSeconds(scrollX: Float, viewWidth: Float, pixelsPerSecond: Float): IntRange {
    val start = floor((scrollX - TimelineLeftPadding).coerceAtLeast(0f) / pixelsPerSecond).toInt()
    val end = ceil((scrollX + viewWidth) / pixelsPerSecond).toInt()
    return start..end.coerceAtLeast(start)
}

internal fun timelineMaxKeyTime(controller: TimelineController): Float =
    controller.allCurves().flatMap { it.keyframes }.maxOfOrNull { it.time } ?: 0f

internal fun Color.toUiColor(alphaMultiplier: Float = 1f): UiColor {
    return UiColor(r, g, b, (a * alphaMultiplier).coerceIn(0f, 1f))
}

internal fun UiColor.withAlpha(multiplier: Float) =
    UiColor(red, green, blue, (alpha * multiplier).coerceIn(0f, 1f))

internal object TimelineColors {
    val Background = UiColor(0.07f, 0.08f, 0.1f, 1f)
    val Panel = UiColor(0.1f, 0.11f, 0.14f, 1f)
    val PanelAlt = UiColor(0.13f, 0.14f, 0.17f, 1f)
    val Row = UiColor(0.11f, 0.12f, 0.14f, 1f)
    val Group = UiColor(0.14f, 0.15f, 0.18f, 1f)
    val Muted = UiColor(0.58f, 0.62f, 0.7f, 1f)
    val Text = UiColor(0.88f, 0.9f, 0.94f, 1f)
    val Accent = UiColor(1f, 0.54f, 0.18f, 1f)
    val Blue = UiColor(0.34f, 0.58f, 0.88f, 1f)
    val Border = UiColor(0.24f, 0.26f, 0.3f, 1f)
    val Danger = UiColor(0.76f, 0.23f, 0.23f, 1f)
    val Grid = UiColor(0.18f, 0.19f, 0.23f, 1f)
    val Handle = UiColor(0.72f, 0.74f, 0.8f, 1f)
}
