package ru.hollowhorizon.hollowengine.client.gui.timeline.ui

import de.fabmax.kool.math.Vec2f
import de.fabmax.kool.math.Vec3f
import de.fabmax.kool.util.Color
import ru.hollowhorizon.hollowengine.client.gui.timeline.AnimTrack
import ru.hollowhorizon.hollowengine.client.gui.timeline.BaseAnimTrack
import ru.hollowhorizon.hollowengine.client.gui.timeline.Keyframe
import ru.hollowhorizon.hollowengine.client.gui.timeline.TrackGroup
import ru.hollowhorizon.hollowengine.client.ui.GenericShape
import ru.hollowhorizon.hollowengine.client.ui.Shape
import ru.hollowhorizon.hollowengine.client.ui.UiColor
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

internal const val TimelineHeaderWidth = 220f
internal const val TimelineRulerHeight = 30f
internal const val TimelineGroupRowHeight = 30f
internal const val TimelineTrackRowHeight = 40f
internal const val TimelineLeftPadding = 24f
internal const val TimelineMinContentWidth = 600f
internal const val TimelineMaxZoom = 500f
internal const val TimelineMinZoom = 10f

internal val TimelineDiamondShape: Shape = GenericShape { size ->
    moveTo(size.width * 0.5f, 0f)
    lineTo(size.width, size.height * 0.5f)
    lineTo(size.width * 0.5f, size.height)
    lineTo(0f, size.height * 0.5f)
    close()
}

internal data class TimelineRow(
    val id: String,
    val label: String,
    val depth: Int,
    val y: Float,
    val height: Float,
    val kind: TimelineRowKind,
    val group: TrackGroup? = null,
    val track: BaseAnimTrack? = null,
    val locked: Boolean = false,
    val visible: Boolean = true,
)

internal enum class TimelineRowKind {
    GROUP,
    TRACK,
}

internal fun timelineRows(groups: List<TrackGroup>): List<TimelineRow> {
    val rows = mutableListOf<TimelineRow>()
    var y = TimelineRulerHeight

    fun appendGroup(group: TrackGroup, depth: Int, parentLocked: Boolean, parentVisible: Boolean) {
        val locked = parentLocked || group.isLocked.value
        val visible = parentVisible && group.isVisible.value
        rows += TimelineRow(
            id = "timeline-group-${rows.size}",
            label = group.nameState.value,
            depth = depth,
            y = y,
            height = TimelineGroupRowHeight,
            kind = TimelineRowKind.GROUP,
            group = group,
            locked = locked,
            visible = visible,
        )
        y += TimelineGroupRowHeight
        if (group.isCollapsed.value) return

        group.children.forEach { appendGroup(it, depth + 1, locked, visible) }
        group.tracks.forEach { track ->
            rows += TimelineRow(
                id = "timeline-track-${rows.size}",
                label = track.nameState.value,
                depth = depth + 1,
                y = y,
                height = TimelineTrackRowHeight,
                kind = TimelineRowKind.TRACK,
                track = track,
                locked = locked || track.isLocked.value,
                visible = visible && track.isVisible.value,
            )
            y += TimelineTrackRowHeight
        }
    }

    groups.forEach { appendGroup(it, 0, parentLocked = false, parentVisible = true) }
    return rows
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

internal fun timelineTimeAt(localX: Float, pixelsPerSecond: Float, workAreaEnd: Float): Float {
    return ((localX - TimelineLeftPadding) / pixelsPerSecond)
        .coerceIn(0f, workAreaEnd)
}

internal fun timelineTimeDelta(deltaX: Float, pixelsPerSecond: Float): Float {
    return deltaX / pixelsPerSecond.coerceAtLeast(1f)
}

internal fun timelineRulerSeconds(scrollX: Float, viewWidth: Float, pixelsPerSecond: Float): IntRange {
    val start = floor((scrollX - TimelineLeftPadding).coerceAtLeast(0f) / pixelsPerSecond).toInt()
    val end = ceil((scrollX + viewWidth) / pixelsPerSecond).toInt()
    return start..end.coerceAtLeast(start)
}

internal fun timelineMaxKeyTime(tracks: List<BaseAnimTrack>): Float {
    return tracks.flatMap { it.getKeysAsList() }.maxOfOrNull { it.time } ?: 0f
}

internal fun timelineKeyValuesEqual(first: Any?, second: Any?): Boolean {
    return when {
        first is Vec2f && second is Vec2f -> {
            abs(first.x - second.x) <= 0.0001f && abs(first.y - second.y) <= 0.0001f
        }

        first is Vec3f && second is Vec3f -> {
            abs(first.x - second.x) <= 0.0001f && abs(first.y - second.y) <= 0.0001f && abs(first.z - second.z) <= 0.0001f
        }

        first is Float && second is Float -> abs(first - second) <= 0.0001f
        else -> first == second
    }
}

internal fun Color.toUiColor(alphaMultiplier: Float = 1f): UiColor {
    return UiColor(r, g, b, (a * alphaMultiplier).coerceIn(0f, 1f))
}

internal fun trackOf(keyframe: Keyframe<*>, tracks: List<BaseAnimTrack>): AnimTrack<*>? {
    return tracks.filterIsInstance<AnimTrack<*>>().firstOrNull { keyframe in it.keyframes }
}

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
}
