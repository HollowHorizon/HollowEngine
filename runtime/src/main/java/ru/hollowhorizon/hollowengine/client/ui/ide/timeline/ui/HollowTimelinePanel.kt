package ru.hollowhorizon.hollowengine.client.ui.ide.timeline.ui

import androidx.compose.runtime.*
import kotlinx.coroutines.isActive
import org.lwjgl.glfw.GLFW
import ru.hollowhorizon.hollowengine.client.ui.*
import ru.hollowhorizon.hollowengine.client.ui.ide.timeline.AnimTrack
import ru.hollowhorizon.hollowengine.client.ui.ide.timeline.BaseAnimTrack
import ru.hollowhorizon.hollowengine.client.ui.ide.timeline.Keyframe
import ru.hollowhorizon.hollowengine.client.ui.ide.timeline.TimelineController
import ru.hollowhorizon.hollowengine.client.ui.ide.timeline.cutscene.CutsceneEditorSession
import ru.hollowhorizon.hollowengine.client.ui.ide.timeline.cutscene.CutsceneStorage
import ru.hollowhorizon.hollowengine.client.ui.layout.UiRect
import ru.hollowhorizon.hollowengine.client.ui.scroll.UiScrollHandle
import ru.hollowhorizon.hollowengine.client.ui.scroll.rememberScrollState
import ru.hollowhorizon.hollowengine.client.ui.style.UiPaint
import ru.hollowhorizon.hollowengine.client.ui.style.UiShadow
import ru.hollowhorizon.hollowengine.client.ui.widgets.ContextMenu
import ru.hollowhorizon.hollowengine.client.ui.widgets.UiDropdownItem
import kotlin.math.round

private const val TimelineZoomWheelStep = 12f
private const val TimelineScrollStep = 44f
private const val TimelineKeyframeSize = 13f

/**
 * Whole-subtree refresh signal for the cutscene panels. The timeline UI reads Kool
 * [de.fabmax.kool.modules.ui2.MutableStateValue]s that Compose cannot observe, so a change in
 * `session.uiRevision` is fanned out by providing it through this STATIC local — changing a static
 * local recomposes the entire content under the provider (exactly the "refresh everything" the old
 * Kool `surface.triggerUpdate()` did), reaching every leaf regardless of parameter stability.
 */
internal val LocalTimelineRevision = staticCompositionLocalOf { 0 }

@Composable
fun CutsceneTimelineDock(session: CutsceneEditorSession, keyboardActive: Boolean = true) {
    // Subscribe to the shared revision so this dock recomposes whenever any panel edits the timeline.
    session.uiRevision.value
    var dialog by remember { mutableStateOf(CutsceneDialog.NONE) }

    // The controller's playback clock is not Compose-observable, so a persistent per-frame loop advances
    // the simulation while playing; onTimeChanged then bumps the shared revision to move the playhead.
    // Delta comes from the Compose frame clock (Kool's Time is only ticked while a Kool surface renders).
    LaunchedEffect(session) {
        var lastNanos = -1L
        while (isActive) {
            withFrameNanos { now ->
                val delta =
                    if (lastNanos < 0L) 0f else ((now - lastNanos) / 1_000_000_000.0).toFloat().coerceIn(0f, 0.1f)
                lastNanos = now
                if (session.timeline.isPlaying) session.update(delta)
            }
        }
    }

    CompositionLocalProvider(LocalTimelineRevision provides session.uiRevision.value) {
        Box(id = "cutscene-timeline-dock", mode = UiBoxMode.STACK, modifier = Modifier.size(100.percent, 100.percent)) {
            HollowTimelineEditor(
                controller = session.timeline,
                refresh = session::invalidateUi,
                onKeyInput = session::onHollowUiKey,
                keyboardActive = keyboardActive,
                onCapture = { session.captureFrame(session.timeline.currentTime) },
                onSave = { dialog = CutsceneDialog.SAVE },
                onLoad = { dialog = CutsceneDialog.LOAD },
            )

            when (dialog) {
                CutsceneDialog.SAVE -> SaveCutsceneDialog(session) { dialog = CutsceneDialog.NONE }
                CutsceneDialog.LOAD -> LoadCutsceneDialog(session) { dialog = CutsceneDialog.NONE }
                CutsceneDialog.NONE -> {}
            }
        }
    }
}

/** The Properties inspector as its own dock panel; shares the session's timeline and revision. */
@Composable
fun CutscenePropertiesDock(session: CutsceneEditorSession) {
    CompositionLocalProvider(LocalTimelineRevision provides session.uiRevision.value) {
        HollowTimelineProperties(
            controller = session.timeline,
            modifier = Modifier.size(100.percent, 100.percent),
            refresh = session::invalidateUi,
        )
    }
}

private enum class CutsceneDialog { NONE, SAVE, LOAD }

@Composable
fun HollowTimelineEditor(
    controller: TimelineController,
    refresh: () -> Unit,
    onKeyInput: (Int, Int) -> Boolean = { _, _ -> false },
    keyboardActive: Boolean = true,
    onCapture: () -> Unit = {},
    onSave: () -> Unit = {},
    onLoad: () -> Unit = {},
) {
    val bump = refresh
    val scroll = rememberScrollState()
    val rows = timelineRows(controller.groups)
    val rowsHeight = rows.sumOf { it.height.toDouble() }.toFloat()
    val tracks = controller.getAllTracks()
    val pxPerSec = controller.pixelsPerSecond
    val contentWidth = timelineContentWidth(
        controller.workAreaEnd,
        timelineMaxKeyTime(tracks),
        pxPerSec,
        TimelineMinContentWidth,
    )

    var laneViewport by remember { mutableStateOf(UiRect.Zero) }
    var laneMenu by remember { mutableStateOf<LaneMenuState?>(null) }
    var isScrubbing by remember { mutableStateOf(false) }
    val scrollContentHeight = rowsHeight + if (laneViewport.height > 0f && rowsHeight > laneViewport.height) {
        TimelineScrollbarClearance
    } else {
        0f
    }
    val showLaneMenu: (UiEvent, AnimTrack<*>, Float) -> Unit = { event, track, time ->
        laneMenu = LaneMenuState(event.x, event.y, track, time)
    }

    // Keep the moving focus (playhead while playing, or a dragged keyframe) in view by panning the canvas.
    val focusContentX = when {
        controller.dragFocusKeyframe != null ->
            TimelineLeftPadding + controller.dragFocusKeyframe!!.time * pxPerSec

        controller.isPlaying || isScrubbing -> TimelineLeftPadding + controller.currentTime * pxPerSec
        else -> null
    }
    if (focusContentX != null) autoPanToContentX(scroll, laneViewport, focusContentX)

    Column(
        id = "cutscene-timeline-root",
        modifier = Modifier.size(100.percent, 100.percent)
            .background(TimelineColors.Background)
            .focusScope()
            .onKeyInput { input ->
                if (keyboardActive) {
                    if (!input.repeat) onKeyInput(input.key, input.modifiers)
                    input.consume()
                }
            },
    ) {
        TimelineToolbar(controller, onCapture, onSave, onLoad, bump)

        Row(modifier = Modifier.size(100.percent, 0.px).grow(1f)) {
            TimelineHeaders(controller, rows, scroll.offsetY, scrollContentHeight, bump)
            Box(modifier = Modifier.size(1.px, 100.percent).background(TimelineColors.Border))

            Column(modifier = Modifier.size(0.px, 100.percent).grow(1f)) {
                // Ruler: horizontally synced to the lane scroll, pinned vertically.
                Box(
                    id = "timeline-ruler-viewport",
                    modifier = Modifier.size(100.percent, TimelineRulerHeight.px)
                        .background(TimelineColors.Group)
                        .clip(),
                ) {
                    Box(
                        modifier = Modifier.position((-scroll.offsetX).px, 0.px)
                            .size(contentWidth.px, TimelineRulerHeight.px),
                    ) {
                        TimeRuler(controller, pxPerSec, contentWidth, bump) { isScrubbing = it }
                    }
                }

                // Lanes: the only real scroll container; ruler and headers mirror its offset.
                Box(
                    id = "timeline-lane-viewport",
                    tags = listOf("timeline-scroll"),
                    modifier = Modifier.size(100.percent, 0.px)
                        .grow(1f)
                        .background(TimelineColors.Background)
                        .clip()
                        .scrollable(state = scroll)
                        .onPlaced { laneViewport = it }
                        .onScroll { event -> handleTimelineScroll(event, controller, scroll, laneViewport, bump) },
                ) {
                    Box(
                        id = "timeline-lane-content",
                        modifier = Modifier.size(contentWidth.px, scrollContentHeight.coerceAtLeast(1f).px),
                    ) {
                        rows.forEach { row ->
                            TimelineLane(row, controller, pxPerSec, contentWidth, bump, showLaneMenu)
                        }
                        WorkAreaShade(controller, pxPerSec, contentWidth, rowsHeight)
                        Playhead(controller, pxPerSec, rowsHeight)
                    }
                }
            }
        }
    }

    laneMenu?.let { menu ->
        ContextMenu(
            id = "cutscene-lane-menu",
            anchorBounds = UiRect(menu.x, menu.y, 0f, 0f),
            items = listOf(
                UiDropdownItem("Add keyframe here", icon = "hollowengine:textures/gui/icons/pulse.svg") {
                    controller.addKeyframe(menu.track, menu.time)
                    refresh()
                },
                UiDropdownItem(
                    "Delete selected",
                    icon = "hollowengine:textures/gui/icons/remove.svg",
                    enabled = controller.selectedKeyframes.isNotEmpty(),
                ) {
                    controller.deleteSelectedKeyframes()
                    refresh()
                },
            ),
            onExpandedChange = { open -> if (!open) laneMenu = null },
        )
    }
}

private class LaneMenuState(val x: Float, val y: Float, val track: AnimTrack<*>, val time: Float)

/**
 * Wheel handling for the lane viewport. The raw scroll event carries no modifiers, so query GLFW.
 * Ctrl = zoom around the cursor; otherwise the wheel pans horizontally (the natural timeline axis) and
 * Shift pans vertically — the inverse of the default. Always consumes so the native scroll never runs.
 */
private fun handleTimelineScroll(
    event: UiEvent,
    controller: TimelineController,
    scroll: UiScrollHandle,
    viewport: UiRect,
    refresh: () -> Unit,
) {
    val modifiers = currentUiKeyModifiers()
    // One of scrollX/scrollY is zero; the runtime already folds the wheel onto X when a modifier is held.
    val wheel = event.scrollX + event.scrollY
    if (wheel == 0f) {
        event.consume()
        return
    }
    if (modifiers and GLFW.GLFW_MOD_CONTROL != 0) {
        val oldZoom = controller.pixelsPerSecond
        val newZoom = (oldZoom - wheel * TimelineZoomWheelStep).coerceIn(TimelineMinZoom, TimelineMaxZoom)
        if (newZoom != oldZoom) {
            val cursorInViewport = event.x - viewport.x
            val cursorContentX = cursorInViewport + scroll.offsetX
            val time = (cursorContentX - TimelineLeftPadding) / oldZoom
            controller.pixelsPerSecond = newZoom
            val newContentX = time * newZoom + TimelineLeftPadding
            scroll.scrollTo(x = (newContentX - cursorInViewport).coerceAtLeast(0f))
            refresh()
        }
        event.consume()
        return
    }
    val amount = wheel * TimelineScrollStep
    if (modifiers and GLFW.GLFW_MOD_SHIFT != 0) {
        scroll.animateScrollBy(deltaY = amount)
    } else {
        scroll.animateScrollBy(deltaX = amount)
    }
    refresh()
    event.consume()
}

@Composable
private fun TimeRuler(
    controller: TimelineController,
    pxPerSec: Float,
    contentWidth: Float,
    refresh: () -> Unit,
    onScrubbingChange: (Boolean) -> Unit,
) {
    Box(
        id = "timeline-ruler",
        modifier = Modifier.size(contentWidth.px, TimelineRulerHeight.px)
            .cursor(UiCursorShape.HAND)
            .onPress { event ->
                if (event.button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return@onPress
                onScrubbingChange(true)
                controller.clearSelection()
                controller.applyCurrentTime(
                    timelineTimeAt(
                        event.localX,
                        pxPerSec,
                        controller.workAreaEnd,
                        event.modifiers
                    )
                )
                refresh()
            }
            .onDrag { event ->
                controller.applyCurrentTime(
                    timelineTimeAt(
                        event.localX,
                        pxPerSec,
                        controller.workAreaEnd,
                        event.modifiers
                    )
                )
                event.consume()
                refresh()
            }
            .onRelease { onScrubbingChange(false) },
    ) {
        val majorStep = rulerMajorStep(pxPerSec)
        val minorPerMajor = 5
        val seconds = timelineRulerSeconds(0f, contentWidth, pxPerSec)
        var second = 0f
        val end = seconds.last.toFloat()
        while (second <= end) {
            val x = TimelineLeftPadding + second * pxPerSec
            Box(
                modifier = Modifier.position(x.px, 16.px)
                    .size(1.px, 14.px)
                    .background(TimelineColors.Muted),
            )
            Text(
                formatSeconds(second),
                modifier = Modifier.position((x + 3f).px, 3.px)
                    .fontSize(9f)
                    .foreground(TimelineColors.Muted),
            )
            if (pxPerSec > 60f) {
                val minorStep = majorStep / minorPerMajor
                for (i in 1 until minorPerMajor) {
                    val mx = TimelineLeftPadding + (second + i * minorStep) * pxPerSec
                    Box(
                        modifier = Modifier.position(mx.px, 22.px)
                            .size(1.px, 8.px)
                            .background(TimelineColors.Border),
                    )
                }
            }
            second += majorStep
        }
        WorkAreaHandle(controller, pxPerSec, refresh)
        // Playhead head riding on the ruler: a thin line topped by a glowing marker.
        val px = TimelineLeftPadding + controller.currentTime * pxPerSec
        Box(
            modifier = Modifier.position((px - 0.5f).px, 0.px)
                .size(1.px, TimelineRulerHeight.px)
                .background(TimelineColors.Accent),
        )
        Box(
            modifier = Modifier.position((px - 6f).px, 0.px)
                .size(12.px, 12.px)
                .shape(PlayheadHeadShape, UiPaint.Color(TimelineColors.Accent))
                .shadow(UiShadow(offset = UiVec3(0f, 0f, 0f), blur = 1.2f, color = PlayheadGlowColor)),
        )
    }
}

private val PlayheadGlowColor = UiColor(1f, 0.54f, 0.18f, 0.7f)

private fun rulerMajorStep(pxPerSec: Float): Float = when {
    pxPerSec < 20f -> 5f
    pxPerSec < 60f -> 1f
    pxPerSec < 160f -> 0.5f
    else -> 0.25f
}

@Composable
private fun TimelineLane(
    row: TimelineRow,
    controller: TimelineController,
    pxPerSec: Float,
    contentWidth: Float,
    refresh: () -> Unit,
    onLaneContextMenu: (UiEvent, AnimTrack<*>, Float) -> Unit,
) {
    val top = row.y - TimelineRulerHeight
    val background = when {
        row.kind == TimelineRowKind.GROUP -> TimelineColors.Group
        row.locked -> UiColor(0.08f, 0.08f, 0.09f, 1f)
        !row.visible -> UiColor(0.09f, 0.09f, 0.1f, 1f)
        else -> TimelineColors.Row
    }
    Box(
        id = "lane-${row.id}",
        modifier = Modifier.position(0.px, top.px)
            .size(contentWidth.px, row.height.px)
            .background(background)
            .onPress { event ->
                val track = row.track as? AnimTrack<*> ?: return@onPress
                if (event.button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && !row.locked) {
                    val time = timelineTimeAt(event.localX, pxPerSec, controller.workAreaEnd, event.modifiers)
                    onLaneContextMenu(event, track, time)
                } else if (event.modifiers and GLFW.GLFW_MOD_CONTROL == 0) {
                    controller.clearSelection()
                }
                refresh()
            },
    ) {
        // Single 1px bottom divider instead of a per-lane border (which doubled up and left a seam).
        Box(
            modifier = Modifier.position(0.px, (row.height - 1f).px)
                .size(contentWidth.px, 1.px)
                .background(TimelineColors.Border),
        )
        (row.track as? AnimTrack<*>)?.let { track ->
            val sortedKeyframes = track.keyframes.sortedBy { it.time }
            KeyframeConnections(track, row, pxPerSec, sortedKeyframes)
            sortedKeyframes.forEachIndexed { index, keyframe ->
                val halfSize = TimelineKeyframeSize * 0.5f
                val leftHalfWidth = sortedKeyframes.getOrNull(index - 1)?.let { previous ->
                    ((keyframe.time - previous.time) * pxPerSec * 0.5f).coerceIn(0f, halfSize)
                } ?: halfSize
                val rightHalfWidth = sortedKeyframes.getOrNull(index + 1)?.let { next ->
                    ((next.time - keyframe.time) * pxPerSec * 0.5f).coerceIn(0f, halfSize)
                } ?: halfSize
                key(keyframe) {
                    TimelineKeyframe(
                        keyframe,
                        track,
                        row,
                        controller,
                        pxPerSec,
                        leftHalfWidth,
                        rightHalfWidth,
                        refresh,
                        onLaneContextMenu,
                    )
                }
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
    leftHalfWidth: Float,
    rightHalfWidth: Float,
    refresh: () -> Unit,
    onContextMenu: (UiEvent, AnimTrack<*>, Float) -> Unit,
) {
    val selected = keyframe in controller.selectedKeyframes
    val color = track.color.toUiColor(if (row.visible) 1f else 0.4f)
    val halfSize = TimelineKeyframeSize * 0.5f
    val x = TimelineLeftPadding + keyframe.time * pxPerSec - leftHalfWidth
    val y = row.height * 0.5f - halfSize
    Box(
        tags = buildList {
            add("timeline-keyframe")
            if (selected) add("selected")
            if (row.locked) add("locked")
        },
        modifier = Modifier.position(x.px, y.px)
            .size((leftHalfWidth + rightHalfWidth).px, TimelineKeyframeSize.px)
            .cursor(if (row.locked) UiCursorShape.DEFAULT else UiCursorShape.MOVE)
            .onPress { event ->
                if (row.locked) return@onPress
                if (event.button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                    if (keyframe !in controller.selectedKeyframes) {
                        controller.selectedKeyframes.clear()
                        controller.selectedKeyframes.add(keyframe)
                        controller.isWorkAreaSelected = false
                    }
                    (track as? AnimTrack<*>)?.let { onContextMenu(event, it, keyframe.time) }
                    event.consume()
                    refresh()
                    return@onPress
                }
                if (event.button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return@onPress
                updateKeyframeSelection(controller, keyframe, event)
                // Capture the whole selection's times so the drag moves the group in sync.
                controller.beginKeyframeDrag(keyframe)
                refresh()
            }
            .onDrag { event ->
                if (!row.locked) {
                    val delta = timelineTimeDelta(event.dragTotalX, pxPerSec)
                    val origin = controller.dragStartTimes?.get(keyframe) ?: keyframe.time
                    controller.applyKeyframeDrag(snapTimelineTime(origin + delta, event.modifiers) - origin)
                    event.consume()
                    refresh()
                }
            }
            .onRelease {
                if (!row.locked && controller.dragFocusKeyframe === keyframe) {
                    controller.endKeyframeDrag()
                    refresh()
                }
            },
    ) {
        Box(
            tags = listOf("timeline-keyframe-visual"),
            modifier = Modifier.position((leftHalfWidth - halfSize).px, 0.px)
                .size(TimelineKeyframeSize.px, TimelineKeyframeSize.px)
                .shape(TimelineDiamondShape)
                .shapeFill(color)
                .inputTransparent(),
        )
    }
}

/**
 * A thin bar connecting neighbouring keyframes on the (compact) lane. A constant hold (equal values)
 * reads brighter; interpolated segments are dimmer. The actual easing curve is previewed in Properties.
 */
@Composable
private fun KeyframeConnections(
    track: AnimTrack<*>,
    row: TimelineRow,
    pxPerSec: Float,
    sortedKeys: List<Keyframe<*>>,
) {
    val alpha = if (row.visible) 1f else 0.25f
    for (index in 0 until sortedKeys.lastIndex) {
        val first = sortedKeys[index]
        val second = sortedKeys[index + 1]
        val x = TimelineLeftPadding + first.time * pxPerSec
        val width = (second.time - first.time) * pxPerSec
        if (width <= 1f) continue
        val hold = timelineKeyValuesEqual(first.value, second.value)
        Box(
            modifier = Modifier.position(x.px, (row.height * 0.5f - 1f).px)
                .size(width.px, 2.px)
                .background(track.color.toUiColor(alpha * (if (hold) 0.8f else 0.4f)))
                .borderRadius(1f),
        )
    }
}

@Composable
private fun WorkAreaHandle(controller: TimelineController, pxPerSec: Float, refresh: () -> Unit) {
    val x = TimelineLeftPadding + controller.workAreaEnd * pxPerSec
    val color = if (controller.isWorkAreaSelected) UiColor.White else TimelineColors.Accent
    // work-area end captured at grab, so total-offset dragging stays in sync past the min bound.
    var dragStartEnd by remember { mutableStateOf(0f) }
    // A ']' bracket capping the usable area: spine on the work-area line, ticks pointing back into it.
    Box(
        id = "timeline-work-area-end",
        mode = UiBoxMode.STACK,
        modifier = Modifier.position((x - 6f).px, 0.px)
            .size(8.px, TimelineRulerHeight.px)
            .cursor(UiCursorShape.RESIZE_HORIZONTAL)
            .onPress {
                controller.selectedKeyframes.clear()
                controller.isWorkAreaSelected = true
                dragStartEnd = controller.workAreaEnd
                refresh()
            }
            .onDrag { event ->
                val maxKeyTime = timelineMaxKeyTime(controller.getAllTracks())
                val raw = (dragStartEnd + timelineTimeDelta(event.dragTotalX, pxPerSec))
                    .coerceAtLeast(maxKeyTime)
                    .coerceAtLeast(0.1f)
                val next = snapTimelineTime(raw, event.modifiers)
                    .coerceAtLeast(maxKeyTime)
                    .coerceAtLeast(0.1f)
                controller.workAreaEnd = if (event.modifiers == 0) round(next * 100f) / 100f else next
                if (controller.currentTime > controller.workAreaEnd) controller.applyCurrentTime(0f)
                controller.isWorkAreaSelected = true
                event.consume()
                refresh()
            },
    ) {
        Box(modifier = Modifier.position(6.px, 0.px).size(2.px, TimelineRulerHeight.px).background(color))
        Box(modifier = Modifier.position(0.px, 0.px).size(6.px, 2.px).background(color))
        Box(modifier = Modifier.position(0.px, (TimelineRulerHeight - 2f).px).size(6.px, 2.px).background(color))
    }
}

@Composable
private fun WorkAreaShade(controller: TimelineController, pxPerSec: Float, contentWidth: Float, rowsHeight: Float) {
    val x = TimelineLeftPadding + controller.workAreaEnd * pxPerSec
    if (x >= contentWidth) return
    Box(
        modifier = Modifier.position(x.px, 0.px)
            .size((contentWidth - x).px, rowsHeight.coerceAtLeast(1f).px)
            .background(UiColor(0f, 0f, 0f, 0.38f)),
    )
}

@Composable
private fun Playhead(controller: TimelineController, pxPerSec: Float, rowsHeight: Float) {
    val x = TimelineLeftPadding + controller.currentTime * pxPerSec
    Box(
        id = "timeline-playhead",
        modifier = Modifier.position((x - 0.5f).px, 0.px)
            .size(1.px, rowsHeight.coerceAtLeast(1f).px)
            .background(TimelineColors.Accent),
    )
}

private fun updateKeyframeSelection(controller: TimelineController, keyframe: Keyframe<*>, event: UiEvent) {
    val ctrl = event.modifiers and GLFW.GLFW_MOD_CONTROL != 0
    when {
        ctrl ->
            if (keyframe in controller.selectedKeyframes) {
                controller.selectedKeyframes.remove(keyframe)
            } else {
                controller.selectedKeyframes.add(keyframe)
            }
        // Plain press on a key that is already part of the selection keeps the group (so it can be
        // dragged together); pressing an unselected key selects just it.
        keyframe !in controller.selectedKeyframes -> {
            controller.selectedKeyframes.clear()
            controller.selectedKeyframes.add(keyframe)
        }
    }
    controller.isWorkAreaSelected = false
}

// ---------------------------------------------------------------------------
// Save / Load dialogs (lightweight overlays over the timeline dock)
// ---------------------------------------------------------------------------

@Composable
private fun SaveCutsceneDialog(session: CutsceneEditorSession, onClose: () -> Unit) {
    var name by remember { mutableStateOf(session.playback.toData().name) }
    DialogFrame("Save cutscene", onClose) {
        Text("Name", modifier = Modifier.fontSize(10f).foreground(TimelineColors.Muted))
        TextField(
            value = name,
            onChange = { name = it },
            modifier = Modifier.size(100.percent, 24.px)
                .background(TimelineColors.Background)
                .border(1.px, TimelineColors.Border, 3f)
                .padding(6.px, 2.px)
                .foreground(TimelineColors.Text)
                .fontSize(11f),
        )
        Row(modifier = Modifier.size(100.percent, UiLength.Auto).gap(8.px).align(horizontal = UiAlign.END)) {
            ToolbarButton("Cancel", "cutscene-save-cancel") { onClose() }
            ToolbarButton("Save", "cutscene-save-confirm", TimelineColors.Blue) {
                if (name.isNotBlank()) {
                    session.exportCutscene("", name.trim())
                    onClose()
                }
            }
        }
    }
}

@Composable
private fun LoadCutsceneDialog(session: CutsceneEditorSession, onClose: () -> Unit) {
    val files = remember { CutsceneStorage.listFiles() }
    DialogFrame("Load cutscene", onClose) {
        if (files.isEmpty()) {
            Text("No saved cutscenes", modifier = Modifier.fontSize(11f).foreground(TimelineColors.Muted))
        } else {
            Column(
                modifier = Modifier.size(100.percent, UiLength.Auto)
                    .gap(2.px)
                    .scrollable(horizontal = false),
            ) {
                files.forEach { file ->
                    Box(
                        modifier = Modifier.size(100.percent, 24.px)
                            .background(TimelineColors.Background)
                            .border(1.px, TimelineColors.Border, 3f)
                            .cursor(UiCursorShape.HAND)
                            .onClick {
                                session.importCutscene(file)
                                onClose()
                                it.consume()
                            },
                    ) {
                        Text(
                            file.removeSuffix(".${CutsceneStorage.EXTENSION}"),
                            modifier = Modifier.align(vertical = UiAlign.CENTER)
                                .margin(8.px, 0.px, 0.px, 0.px)
                                .fontSize(11f)
                                .foreground(TimelineColors.Text),
                        )
                    }
                }
            }
        }
        Row(modifier = Modifier.size(100.percent, UiLength.Auto).align(horizontal = UiAlign.END)) {
            ToolbarButton("Close", "cutscene-load-close") { onClose() }
        }
    }
}

@Composable
private fun DialogFrame(title: String, onClose: () -> Unit, content: HollowUiContent) {
    // Scrim: click outside to dismiss.
    Box(
        mode = UiBoxMode.STACK,
        modifier = Modifier.size(100.percent, 100.percent)
            .background(UiColor(0f, 0f, 0f, 0.45f))
            .layer(500)
            .onClick { onClose() },
    ) {
        Column(
            id = "cutscene-dialog",
            modifier = Modifier.size(320.px, UiLength.Auto)
                .align(UiAlign.CENTER, UiAlign.CENTER)
                .background(TimelineColors.Panel)
                .border(1.px, TimelineColors.Border, 6f)
                .padding(14.px)
                .gap(8.px)
                // Swallow clicks so they don't reach the scrim.
                .onClick { it.consume() },
        ) {
            Text(title, modifier = Modifier.fontSize(13f).foreground(TimelineColors.Text))
            content()
        }
    }
}
