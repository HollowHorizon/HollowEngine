package ru.hollowhorizon.hollowengine.client.gui.timeline.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import org.lwjgl.glfw.GLFW
import ru.hollowhorizon.hollowengine.client.gui.timeline.AnimTrack
import ru.hollowhorizon.hollowengine.client.gui.timeline.BaseAnimTrack
import ru.hollowhorizon.hollowengine.client.gui.timeline.Keyframe
import ru.hollowhorizon.hollowengine.client.gui.timeline.TimelineController
import ru.hollowhorizon.hollowengine.client.gui.timeline.cutscene.CutsceneEditorSession
import ru.hollowhorizon.hollowengine.client.ui.Box
import ru.hollowhorizon.hollowengine.client.ui.Column
import ru.hollowhorizon.hollowengine.client.ui.Modifier
import ru.hollowhorizon.hollowengine.client.ui.Row
import ru.hollowhorizon.hollowengine.client.ui.Text
import ru.hollowhorizon.hollowengine.client.ui.UiColor
import ru.hollowhorizon.hollowengine.client.ui.UiCursorShape
import ru.hollowhorizon.hollowengine.client.ui.UiEvent
import ru.hollowhorizon.hollowengine.client.ui.UiPaint
import ru.hollowhorizon.hollowengine.client.ui.percent
import ru.hollowhorizon.hollowengine.client.ui.px
import kotlin.math.round

@Composable
fun CutsceneTimelineDock(session: CutsceneEditorSession) {
    HollowTimelineEditor(
        controller = session.timeline,
        onCapture = {
            session.captureFrame(session.timeline.currentTime.value)
        },
        onControllerChanged = {
            session.syncPlaybackFromTimeline()
        },
        onPreviewChanged = {
            session.updatePreviewState()
        },
    )
}

@Composable
fun HollowTimelineEditor(
    controller: TimelineController,
    onCapture: () -> Unit = {},
    onControllerChanged: () -> Unit = {},
    onPreviewChanged: () -> Unit = {},
) {
    val revision = remember { mutableStateOf(0) }
    val bump = { revision.value += 1 }
    revision.value

    controller.onChanged = {
        onControllerChanged()
        bump()
    }
    controller.onTimeChanged = {
        onControllerChanged()
        bump()
    }
    controller.onPreviewChanged = {
        onPreviewChanged()
        bump()
    }

    Row(
        id = "cutscene-timeline-root",
        modifier = Modifier.then(
            Modifier.size(100.percent, 100.percent),
            Modifier.background(TimelineColors.Background),
        ),
    ) {
        Column(modifier = Modifier.then(Modifier.size(0.px, 100.percent), Modifier.grow(1f))) {
            TimelineToolbar(controller, onCapture, bump)
            Row(modifier = Modifier.then(Modifier.size(100.percent, 0.px), Modifier.grow(1f))) {
                TimelineHeaderList(controller, bump)
                TimelineArea(controller, bump)
            }
        }
        HollowTimelineProperties(controller, modifier = Modifier.size(300.px, 100.percent), refresh = bump)
    }
}

@Composable
private fun TimelineArea(controller: TimelineController, refresh: () -> Unit) {
    val rows = timelineRows(controller.groups)
    val tracks = controller.getAllTracks()
    val pxPerSec = controller.pixelsPerSecond.value
    val contentHeight = (TimelineRulerHeight + rows.sumOf { it.height.toDouble() }).toFloat()
    val contentWidth = timelineContentWidth(controller.workAreaEnd.value, timelineMaxKeyTime(tracks), pxPerSec, TimelineMinContentWidth)

    Box(
        id = "timeline-scroll",
        modifier = Modifier.then(
            Modifier.size(0.px, 100.percent),
            Modifier.grow(1f),
            Modifier.background(TimelineColors.Background),
            Modifier.input(scrollable = true),
            Modifier.clip(),
            Modifier.onScroll { event ->
                if (event.modifiers and GLFW.GLFW_MOD_CONTROL != 0) {
                    controller.pixelsPerSecond.set(
                        (controller.pixelsPerSecond.value + event.scrollY * 8f).coerceIn(TimelineMinZoom, TimelineMaxZoom)
                    )
                    event.consume()
                    refresh()
                }
            },
        ),
    ) {
        Box(id = "timeline-content", modifier = Modifier.size(contentWidth.px, contentHeight.px)) {
            TimeRuler(controller, pxPerSec, contentWidth, refresh)
            rows.forEach { row ->
                TimelineLane(row, controller, pxPerSec, contentWidth, refresh)
            }
            WorkAreaShade(controller, pxPerSec, contentWidth)
            Playhead(controller, pxPerSec, contentHeight)
        }
    }
}

@Composable
private fun TimeRuler(controller: TimelineController, pxPerSec: Float, contentWidth: Float, refresh: () -> Unit) {
    Box(
        id = "timeline-ruler",
        modifier = Modifier.then(
            Modifier.position(0.px, 0.px),
            Modifier.size(contentWidth.px, TimelineRulerHeight.px),
            Modifier.background(TimelineColors.Group),
            Modifier.input(clickable = true, draggable = true),
            Modifier.cursor(UiCursorShape.HAND),
            Modifier.onPress { event ->
                controller.clearSelection()
                controller.setCurrentTime(timelineTimeAt(event.localX, pxPerSec, controller.workAreaEnd.value))
                refresh()
            },
            Modifier.onClick { event ->
                controller.clearSelection()
                controller.setCurrentTime(timelineTimeAt(event.localX, pxPerSec, controller.workAreaEnd.value))
                event.consume()
                refresh()
            },
            Modifier.onDrag { event ->
                controller.setCurrentTime(timelineTimeAt(event.localX, pxPerSec, controller.workAreaEnd.value))
                event.consume()
                refresh()
            },
        ),
    ) {
        val seconds = timelineRulerSeconds(0f, contentWidth, pxPerSec)
        seconds.forEach { second ->
            val x = TimelineLeftPadding + second * pxPerSec
            Box(modifier = Modifier.then(Modifier.position(x.px, 14.px), Modifier.size(1.px, 16.px), Modifier.background(TimelineColors.Muted)))
            Text(second.toString(), modifier = Modifier.then(Modifier.position((x + 4f).px, 4.px), Modifier.fontSize(10f), Modifier.foreground(TimelineColors.Muted)))
        }
        WorkAreaHandle(controller, pxPerSec, refresh)
    }
}

@Composable
private fun TimelineLane(
    row: TimelineRow,
    controller: TimelineController,
    pxPerSec: Float,
    contentWidth: Float,
    refresh: () -> Unit,
) {
    val background = when {
        row.kind == TimelineRowKind.GROUP -> TimelineColors.Group
        row.locked -> UiColor(0.08f, 0.08f, 0.09f, 1f)
        !row.visible -> UiColor(0.09f, 0.09f, 0.1f, 1f)
        else -> TimelineColors.Row
    }
    Box(
        id = "lane-${row.id}",
        modifier = Modifier.then(
            Modifier.position(0.px, row.y.px),
            Modifier.size(contentWidth.px, row.height.px),
            Modifier.background(background),
            Modifier.border(1.px, TimelineColors.Border),
            Modifier.input(clickable = row.track != null, draggable = row.track != null && !row.locked),
            Modifier.onPress { event ->
                val track = row.track as? AnimTrack<*> ?: return@onPress
                if (event.button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && !row.locked) {
                    controller.trackContextMenuTime = timelineTimeAt(event.localX, pxPerSec, controller.workAreaEnd.value)
                    controller.addKeyframe(track, controller.trackContextMenuTime ?: controller.currentTime.value)
                } else {
                    controller.clearSelection()
                }
                refresh()
            },
            Modifier.onClick { event ->
                val track = row.track as? AnimTrack<*> ?: return@onClick
                if (event.button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && !row.locked) {
                    controller.trackContextMenuTime = timelineTimeAt(event.localX, pxPerSec, controller.workAreaEnd.value)
                    controller.addKeyframe(track, controller.trackContextMenuTime ?: controller.currentTime.value)
                } else {
                    controller.clearSelection()
                }
                event.consume()
                refresh()
            },
        ),
    ) {
        (row.track as? AnimTrack<*>)?.let { track ->
            KeyframeConnections(track, row, pxPerSec)
            track.keyframes.forEach { keyframe ->
                TimelineKeyframe(keyframe, track, row, controller, pxPerSec, refresh)
            }
        }
    }
}

@Composable
private fun TimelineKeyframe(
    keyframe: Keyframe<*>,
    track: BaseAnimTrack,
    row: TimelineRow,
    controller: TimelineController,
    pxPerSec: Float,
    refresh: () -> Unit,
) {
    val selected = keyframe in controller.selectedKeyframes
    val color = track.color.toUiColor(if (row.visible) 1f else 0.4f)
    val size = if (selected) 18f else 14f
    val x = TimelineLeftPadding + keyframe.time * pxPerSec - size * 0.5f
    val y = row.height * 0.5f - size * 0.5f
    Box(
        id = "keyframe-${track.nameState.value}-${keyframe.time}",
        modifier = Modifier.then(
            Modifier.position(x.px, y.px),
            Modifier.size(size.px, size.px),
            Modifier.shape(TimelineDiamondShape, UiPaint.Color(color), UiPaint.Color(if (selected) UiColor.White else TimelineColors.Background), 1.px),
            Modifier.input(hoverable = true, clickable = true, draggable = !row.locked),
            Modifier.cursor(if (row.locked) UiCursorShape.DEFAULT else UiCursorShape.MOVE),
            Modifier.onPress { event ->
                if (!row.locked) {
                    selectKeyframe(controller, keyframe, event)
                    refresh()
                }
            },
            Modifier.onClick { event ->
                if (!row.locked) selectKeyframe(controller, keyframe, event)
                event.consume()
                refresh()
            },
            Modifier.onDrag { event ->
                if (!row.locked) {
                    if (keyframe !in controller.selectedKeyframes) {
                        controller.selectedKeyframes.clear()
                        controller.selectedKeyframes.add(keyframe)
                    }
                    controller.moveKeyframe(track, keyframe, keyframe.time + timelineTimeDelta(event.deltaX, pxPerSec))
                    event.consume()
                    refresh()
                }
            },
        ),
    )
}

@Composable
private fun KeyframeConnections(track: AnimTrack<*>, row: TimelineRow, pxPerSec: Float) {
    val sortedKeys = track.keyframes.sortedBy { it.time }
    for (index in 0 until sortedKeys.lastIndex) {
        val first = sortedKeys[index]
        val second = sortedKeys[index + 1]
        if (!timelineKeyValuesEqual(first.value, second.value)) continue
        val x = TimelineLeftPadding + first.time * pxPerSec
        val width = (second.time - first.time) * pxPerSec
        if (width <= 1f) continue
        Box(
            modifier = Modifier.then(
                Modifier.position(x.px, (row.height * 0.5f - 1f).px),
                Modifier.size(width.px, 2.px),
                Modifier.background(track.color.toUiColor(if (row.visible) 0.75f else 0.2f)),
            ),
        )
    }
}

@Composable
private fun WorkAreaHandle(controller: TimelineController, pxPerSec: Float, refresh: () -> Unit) {
    val x = TimelineLeftPadding + controller.workAreaEnd.value * pxPerSec
    Box(
        id = "timeline-work-area-end",
        modifier = Modifier.then(
            Modifier.position((x - 5f).px, 0.px),
            Modifier.size(10.px, TimelineRulerHeight.px),
            Modifier.shape(TimelineDiamondShape, UiPaint.Color(if (controller.isWorkAreaSelected.value) UiColor.White else TimelineColors.Accent)),
            Modifier.input(clickable = true, draggable = true),
            Modifier.cursor(UiCursorShape.RESIZE_HORIZONTAL),
            Modifier.onPress {
                controller.selectedKeyframes.clear()
                controller.isWorkAreaSelected.set(true)
                refresh()
            },
            Modifier.onClick { event ->
                controller.selectedKeyframes.clear()
                controller.isWorkAreaSelected.set(true)
                event.consume()
                refresh()
            },
            Modifier.onDrag { event ->
                val maxKeyTime = timelineMaxKeyTime(controller.getAllTracks())
                val next = (controller.workAreaEnd.value + timelineTimeDelta(event.deltaX, pxPerSec))
                    .coerceAtLeast(maxKeyTime)
                    .coerceAtLeast(0.1f)
                controller.workAreaEnd.set(round(next * 100f) / 100f)
                if (controller.currentTime.value > controller.workAreaEnd.value) controller.setCurrentTime(0f)
                controller.isWorkAreaSelected.set(true)
                event.consume()
                refresh()
            },
        ),
    )
}

@Composable
private fun WorkAreaShade(controller: TimelineController, pxPerSec: Float, contentWidth: Float) {
    val x = TimelineLeftPadding + controller.workAreaEnd.value * pxPerSec
    if (x >= contentWidth) return
    Box(
        modifier = Modifier.then(
            Modifier.position(x.px, TimelineRulerHeight.px),
            Modifier.size((contentWidth - x).px, 100.percent),
            Modifier.background(UiColor(0f, 0f, 0f, 0.38f)),
        ),
    )
}

@Composable
private fun Playhead(controller: TimelineController, pxPerSec: Float, contentHeight: Float) {
    val x = TimelineLeftPadding + controller.currentTime.value * pxPerSec
    Box(
        id = "timeline-playhead",
        modifier = Modifier.then(
            Modifier.position(x.px, 0.px),
            Modifier.size(2.px, contentHeight.px),
            Modifier.background(TimelineColors.Accent),
        ),
    )
}

private fun selectKeyframe(controller: TimelineController, keyframe: Keyframe<*>, event: UiEvent) {
    val ctrl = event.modifiers and GLFW.GLFW_MOD_CONTROL != 0
    if (ctrl) {
        if (keyframe in controller.selectedKeyframes) {
            controller.selectedKeyframes.remove(keyframe)
        } else {
            controller.selectedKeyframes.add(keyframe)
        }
    } else {
        controller.selectedKeyframes.clear()
        controller.selectedKeyframes.add(keyframe)
    }
    controller.isWorkAreaSelected.set(false)
}
