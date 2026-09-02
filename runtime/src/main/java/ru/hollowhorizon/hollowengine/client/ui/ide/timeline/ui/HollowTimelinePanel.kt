package ru.hollowhorizon.hollowengine.client.ui.ide.timeline.ui

import androidx.compose.runtime.*
import kotlinx.coroutines.isActive
import org.lwjgl.glfw.GLFW
import ru.hollowhorizon.hollowengine.client.ui.*
import ru.hollowhorizon.hollowengine.client.ui.ide.timeline.AnimLayer
import ru.hollowhorizon.hollowengine.client.ui.ide.timeline.AnimProperty
import ru.hollowhorizon.hollowengine.client.ui.ide.timeline.TimelineController
import ru.hollowhorizon.hollowengine.client.ui.ide.timeline.TimelineViewMode
import ru.hollowhorizon.hollowengine.client.ui.ide.timeline.cutscene.CutsceneEditorSession
import ru.hollowhorizon.hollowengine.client.ui.ide.timeline.cutscene.CutsceneStorage
import ru.hollowhorizon.hollowengine.client.ui.layout.UiRect
import ru.hollowhorizon.hollowengine.client.ui.scroll.UiScrollHandle
import ru.hollowhorizon.hollowengine.client.ui.scroll.rememberScrollState
import ru.hollowhorizon.hollowengine.client.ui.style.UiPaint
import ru.hollowhorizon.hollowengine.client.ui.style.UiShadow
import ru.hollowhorizon.hollowengine.client.utils.lang
import kotlin.math.round

private const val TimelineZoomWheelStep = 12f
private const val TimelineScrollStep = 44f
private const val TimelineValueZoomStep = 0.12f
private const val TimelineMinValueSpan = 0.001f
private const val TimelineMaxValueSpan = 100_000f

internal val LocalTimelineRevision = staticCompositionLocalOf { 0 }

@Composable
fun CutsceneTimelineDock(session: CutsceneEditorSession, keyboardActive: Boolean = true) {
    session.uiRevision.value
    var dialog by remember { mutableStateOf(CutsceneDialog.NONE) }
    var layerSettings by remember { mutableStateOf<AnimLayer?>(null) }
    var propertySettings by remember { mutableStateOf<AnimProperty<*>?>(null) }

    LaunchedEffect(session) {
        var lastNanos = -1L
        while (isActive) {
            withFrameNanos { now ->
                val delta =
                    if (lastNanos < 0L) 0f else ((now - lastNanos) / 1_000_000_000.0).toFloat().coerceIn(0f, 0.1f)
                lastNanos = now
                if (session.timeline.isPlaying) session.update(delta)
                if (session.timeline.curveAxis.advance(now)) session.invalidateUi()
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
                onLayerSettings = { layerSettings = it },
                onPropertySettings = { propertySettings = it },
            )

            when (dialog) {
                CutsceneDialog.SAVE -> SaveCutsceneDialog(session) { dialog = CutsceneDialog.NONE }
                CutsceneDialog.LOAD -> LoadCutsceneDialog(session) { dialog = CutsceneDialog.NONE }
                CutsceneDialog.NONE -> {}
            }
            layerSettings?.let { layer ->
                LayerSettingsDialog(session.timeline, layer, session::invalidateUi) { layerSettings = null }
            }
            propertySettings?.let { property ->
                PropertySettingsDialog(session.timeline, property, session::invalidateUi) { propertySettings = null }
            }
        }
    }
}

/** The Properties inspector as its own dock panel; shares the session's timeline and revision. */
@Composable
fun CutscenePropertiesDock(session: CutsceneEditorSession) {
    CompositionLocalProvider(LocalTimelineRevision provides session.uiRevision.value) {
        HollowTimelineProperties(
            session = session,
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
    onLayerSettings: (AnimLayer) -> Unit = {},
    onPropertySettings: (AnimProperty<*>) -> Unit = {},
) {
    val bump = refresh
    val scroll = rememberScrollState()
    val headerScroll = rememberScrollState()
    val rows = timelineRows(controller)
    val rowsHeight = rows.sumOf { it.height.toDouble() }.toFloat()
    val pxPerSec = controller.pixelsPerSecond
    val contentWidth = timelineContentWidth(
        controller.workAreaEnd,
        timelineMaxKeyTime(controller),
        pxPerSec,
        TimelineMinContentWidth,
    )

    val isCurveView = controller.viewMode == TimelineViewMode.CURVES
    var laneViewport by remember { mutableStateOf(UiRect.Zero) }
    var isScrubbing by remember { mutableStateOf(false) }
    val scrollContentHeight = rowsHeight + if (laneViewport.height > 0f && rowsHeight > laneViewport.height) {
        TimelineScrollbarClearance
    } else {
        0f
    }
    val visibleTimes = visibleTimeRange(scroll.offsetX, laneViewport.width, pxPerSec)

    // Keep the moving focus (playhead while playing, or a dragged keyframe) in view by panning.
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
            TimelineHeaders(
                controller = controller,
                rows = rows,
                scroll = headerScroll,
                verticalOffset = if (isCurveView) headerScroll.offsetY else scroll.offsetY,
                ownsVerticalScroll = isCurveView,
                contentHeight = scrollContentHeight,
                onLayerSettings = onLayerSettings,
                onPropertySettings = onPropertySettings,
                refresh = bump,
            )
            HeaderSplitter(controller, bump)

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

                // Lanes: the only real scroll container; the ruler mirrors its offset.
                Box(
                    id = "timeline-lane-viewport",
                    tags = listOf("timeline-scroll"),
                    modifier = Modifier.size(100.percent, 0.px)
                        .grow(1f)
                        .background(TimelineColors.Background)
                        .clip()
                        .scrollable(state = scroll, vertical = !isCurveView)
                        .onPlaced { laneViewport = it }
                        .onScroll { event -> handleTimelineScroll(event, controller, scroll, laneViewport, bump) },
                ) {
                    if (isCurveView) {
                        TimelineCurveGraph(
                            controller = controller,
                            lanes = curveLanes(rows, controller),
                            pxPerSec = pxPerSec,
                            contentWidth = contentWidth,
                            height = laneViewport.height,
                            scrollX = scroll.offsetX,
                            visibleTimes = visibleTimes,
                            scroll = scroll,
                            refresh = bump,
                        )
                    } else {
                        TimelineLanes(
                            controller = controller,
                            rows = rows,
                            pxPerSec = pxPerSec,
                            contentWidth = contentWidth,
                            contentHeight = scrollContentHeight,
                            rowsHeight = rowsHeight,
                            visibleTimes = visibleTimes,
                            scroll = scroll,
                            refresh = bump,
                        )
                    }
                }
            }
        }
    }
}

/** Drag to give the track list more or less room. */
@Composable
private fun HeaderSplitter(controller: TimelineController, refresh: () -> Unit) {
    val start = remember { floatArrayOf(controller.headerWidth) }
    Box(
        id = "timeline-header-splitter",
        modifier = Modifier.size(4.px, 100.percent)
            .background(TimelineColors.Border)
            .cursor(UiCursorShape.RESIZE_HORIZONTAL)
            .input(hoverable = true, draggable = true)
            .onPress { start[0] = controller.headerWidth }
            .onDrag { event ->
                controller.headerWidth = (start[0] + event.dragTotalX)
                    .coerceIn(TimelineMinHeaderWidth, TimelineMaxHeaderWidth)
                event.consume()
                refresh()
            },
    )
}

/**
 * Wheel handling for the lane viewport. The raw scroll event carries no modifiers, so query GLFW.
 * Ctrl = zoom time around the cursor; in the curve editor Shift zooms the value axis and Alt pans it.
 * Otherwise the wheel pans horizontally (the natural timeline axis) and Shift pans vertically.
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

    if (controller.viewMode == TimelineViewMode.CURVES && viewport.height > 0f) {
        val axis = controller.curveAxis
        if (modifiers and GLFW.GLFW_MOD_SHIFT != 0) {
            val height = viewport.height
            val localY = event.y - viewport.y
            val cursorValue = curveYToValue(localY, controller.curveValueCenter, controller.curveValueSpan, height)
            val span = (axis.targetSpan * (1f + wheel * TimelineValueZoomStep))
                .coerceIn(TimelineMinValueSpan, TimelineMaxValueSpan)
            axis.glideTo(cursorValue + (localY - height * 0.5f) / height * span, span)
            refresh()
            event.consume()
            return
        }
        if (modifiers and GLFW.GLFW_MOD_ALT != 0) {
            axis.glideTo(axis.targetCenter - wheel * axis.targetSpan * 0.05f, axis.targetSpan)
            refresh()
            event.consume()
            return
        }
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
                    timelineTimeAt(event.localX, pxPerSec, controller.workAreaEnd, event.modifiers)
                )
                refresh()
            }
            .onDrag { event ->
                controller.applyCurrentTime(
                    timelineTimeAt(event.localX, pxPerSec, controller.workAreaEnd, event.modifiers)
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
                val maxKeyTime = timelineMaxKeyTime(controller)
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
internal fun Playhead(controller: TimelineController, pxPerSec: Float, rowsHeight: Float) {
    val x = TimelineLeftPadding + controller.currentTime * pxPerSec
    Box(
        id = "timeline-playhead",
        modifier = Modifier.position((x - 0.5f).px, 0.px)
            .size(1.px, rowsHeight.coerceAtLeast(1f).px)
            .background(TimelineColors.Accent)
            .inputTransparent(),
    )
}

// ---------------------------------------------------------------------------
// Save / Load dialogs (lightweight overlays over the timeline dock)
// ---------------------------------------------------------------------------

@Composable
private fun SaveCutsceneDialog(session: CutsceneEditorSession, onClose: () -> Unit) {
    var name by remember { mutableStateOf(session.playback.toData().name) }
    DialogFrame(CutsceneLang.SAVE_TITLE.lang, onClose) {
        Text(CutsceneLang.NAME.lang, modifier = Modifier.fontSize(10f).foreground(TimelineColors.Muted))
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
            ToolbarButton(CutsceneLang.CANCEL.lang, "cutscene-save-cancel") { onClose() }
            ToolbarButton(CutsceneLang.SAVE.lang, "cutscene-save-confirm", TimelineColors.Blue) {
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
    DialogFrame(CutsceneLang.LOAD_TITLE.lang, onClose) {
        if (files.isEmpty()) {
            Text(CutsceneLang.NO_CUTSCENES.lang, modifier = Modifier.fontSize(11f).foreground(TimelineColors.Muted))
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
            ToolbarButton(CutsceneLang.CLOSE.lang, "cutscene-load-close") { onClose() }
        }
    }
}

@Composable
internal fun DialogFrame(title: String, onClose: () -> Unit, content: HollowUiContent) {
    val viewport = LocalUiViewport.current
    Popup(
        anchorBounds = viewport,
        alignment = CenteredOnAnchor,
        id = "cutscene-dialog-popup",
        layer = 100,
        modal = true,
        onDismiss = onClose,
    ) {
        Column(
            id = "cutscene-dialog",
            modifier = Modifier.size(340.px, UiLength.Auto)
                .maxSize(height = (viewport.height - 80f).coerceAtLeast(120f).px)
                .background(TimelineColors.Panel)
                .border(1.px, TimelineColors.Border, 6f)
                .padding(14.px)
                .gap(8.px)
                .scrollable(horizontal = false),
        ) {
            Text(title, modifier = Modifier.fontSize(13f).foreground(TimelineColors.Text))
            content()
        }
    }
}

private val CenteredOnAnchor = UiPopupAlignment(
    anchorHorizontal = UiAlign.CENTER,
    anchorVertical = UiAlign.CENTER,
    popupHorizontal = UiAlign.CENTER,
    popupVertical = UiAlign.CENTER,
)
