package ru.hollowhorizon.hollowengine.client.ui.ide.timeline.ui

import androidx.compose.runtime.*
import org.lwjgl.glfw.GLFW
import ru.hollowhorizon.hollowengine.client.ui.*
import ru.hollowhorizon.hollowengine.client.ui.docking.DockTags
import ru.hollowhorizon.hollowengine.client.ui.docking.LocalDockPanelTitle
import ru.hollowhorizon.hollowengine.client.ui.ide.timeline.*
import ru.hollowhorizon.hollowengine.client.ui.scroll.UiScrollHandle
import ru.hollowhorizon.hollowengine.client.ui.style.UiTextOverflow
import ru.hollowhorizon.hollowengine.client.ui.widgets.UiDropdown
import ru.hollowhorizon.hollowengine.client.ui.widgets.UiDropdownItem
import ru.hollowhorizon.hollowengine.client.ui.widgets.tooltipOnHover
import ru.hollowhorizon.hollowengine.client.utils.lang

private const val PlayIcon = "hollowengine:textures/gui/icons/play.svg"
private const val PauseIcon = "hollowengine:textures/gui/icons/pause.svg"
private const val StartIcon = "hollowengine:textures/gui/icons/step_backward.svg"
private const val EndIcon = "hollowengine:textures/gui/icons/step_forward.svg"
private const val ZoomInIcon = "hollowengine:textures/gui/icons/zoom_in.svg"
private const val ZoomOutIcon = "hollowengine:textures/gui/icons/zoom_out.svg"
private const val MenuIcon = "hollowengine:textures/gui/icons/options.svg"
private const val DopeSheetIcon = "hollowengine:textures/gui/icons/layers.svg"
private const val CurvesIcon = "hollowengine:textures/gui/icons/graph.svg"
private const val FrameCurvesIcon = "hollowengine:textures/gui/icons/maximize.svg"
private const val PulseIcon = "hollowengine:textures/gui/icons/pulse.svg"
private const val SettingsIcon = "hollowengine:textures/gui/icons/general.svg"

/**
 * Which of the shared timeline controls make sense for what is being edited.
 */
class TimelineFeatures(
    val capture: Boolean = true,
    val storage: Boolean = true,
    val cameraPreview: Boolean = true,
    /** Play, pause and the jumps to either end; a curve with no clock has no use for them. */
    val playback: Boolean = true,
) {
    companion object {
        val CUTSCENE = TimelineFeatures()
    }
}

@Composable
internal fun TimelineToolbar(
    controller: TimelineController,
    onCapture: () -> Unit,
    onSave: () -> Unit,
    onLoad: () -> Unit,
    refresh: () -> Unit,
    features: TimelineFeatures = TimelineFeatures.CUTSCENE,
) {
    var menuOpen by remember { mutableStateOf(false) }

    val parked = LocalDockPanelTitle.current
    Row(
        id = parked?.headerId ?: "cutscene-timeline-toolbar",
        modifier = Modifier.size(100.percent, 28.px).alignItems(vertical = UiAlign.CENTER)
            .background(TimelineColors.Panel).border(1.px, TimelineColors.Border).padding(6.px, 0.px).gap(4.px)
            .then(parked?.dragHandle ?: Modifier),
    ) {
        parked?.let { panel ->
            panel.icon?.let { icon -> Image(icon, tags = listOf(DockTags.PinnedHeaderIcon)) }
            Text(panel.title, tags = listOf(DockTags.PinnedHeaderLabel), modifier = Modifier.textWrap(false))
            TimelineSeparator()
        }
        if (features.playback) {
            ToolbarIcon(StartIcon, "timeline-start") {
                controller.isPlaying = false
                controller.applyCurrentTime(0f)
                refresh()
            }
            ToolbarIcon(
                if (controller.isPlaying) PauseIcon else PlayIcon,
                "timeline-play",
                active = controller.isPlaying,
            ) {
                controller.togglePlayback()
                refresh()
            }
            ToolbarIcon(EndIcon, "timeline-end") {
                controller.isPlaying = false
                controller.applyCurrentTime(controller.workAreaEnd)
                refresh()
            }

            TimelineSeparator()
        }

        ToolbarIcon(
            DopeSheetIcon,
            "timeline-view-dope",
            active = controller.viewMode == TimelineViewMode.DOPE_SHEET,
            tooltip = CutsceneLang.VIEW_DOPE_SHEET.lang,
        ) {
            controller.viewMode = TimelineViewMode.DOPE_SHEET
            refresh()
        }
        ToolbarIcon(
            CurvesIcon,
            "timeline-view-curves",
            active = controller.viewMode == TimelineViewMode.CURVES,
            tooltip = CutsceneLang.VIEW_CURVES.lang,
        ) {
            controller.enterCurveView()
            refresh()
        }

        TimelineSeparator()

        if (features.playback) Text(
            "${formatSeconds(controller.currentTime)} / ${formatSeconds(controller.workAreaEnd)}",
            modifier = Modifier.align(vertical = UiAlign.CENTER).fontSize(11f).foreground(TimelineColors.Text),
        )

        // Push the trailing controls to the right edge.
        Box(modifier = Modifier.size(0.px, 1.px).grow(1f))

        if (features.capture) {
            ToolbarIcon(PulseIcon, "timeline-capture", tooltip = CutsceneLang.CAPTURE_KEYFRAME.lang) {
                onCapture()
                refresh()
            }

            TimelineSeparator()
        }

        if (controller.viewMode == TimelineViewMode.CURVES) {
            ToolbarIcon(FrameCurvesIcon, "timeline-frame-curves", tooltip = CutsceneLang.FRAME_CURVES.lang) {
                controller.frameCurves()
                refresh()
            }
        }

        ToolbarIcon(ZoomOutIcon, "timeline-zoom-out") {
            zoomAroundCenter(controller, 1f / TimelineZoomButtonFactor)
            refresh()
        }
        ToolbarIcon(ZoomInIcon, "timeline-zoom-in") {
            zoomAroundCenter(controller, TimelineZoomButtonFactor)
            refresh()
        }

        TimelineSeparator()

        // Everything that doesn't fit on the bar lives in an overflow menu.
        UiDropdown(
            id = "timeline-overflow",
            label = "",
            icon = MenuIcon,
            expanded = menuOpen,
            onExpandedChange = { menuOpen = it },
            tags = listOf("timeline-overflow"),
            items = listOfNotNull(
                if (!features.capture) null else UiDropdownItem(CutsceneLang.CAPTURE_KEYFRAME.lang, icon = PulseIcon) {
                    onCapture(); refresh()
                },
                UiDropdownItem(
                    CutsceneLang.DELETE_SELECTED.lang,
                    icon = "hollowengine:textures/gui/icons/remove.svg",
                    enabled = controller.selectedKeyframes.isNotEmpty(),
                ) {
                    controller.deleteSelectedKeyframes(); refresh()
                },
                UiDropdownItem(
                    CutsceneLang.SMOOTH_SELECTED.lang,
                    icon = CurvesIcon,
                    enabled = controller.canEditSelectedCurves,
                ) {
                    controller.smoothSelectedKeyframes(); refresh()
                },
                if (!features.cameraPreview) null else UiDropdownItem(
                    if (controller.isCameraPreviewEnabled) CutsceneLang.CAMERA_PREVIEW_ON.lang
                    else CutsceneLang.CAMERA_PREVIEW_OFF.lang,
                    icon = "hollowengine:textures/gui/icons/film.svg",
                    closeOnClick = false,
                ) {
                    controller.applyCameraPreviewEnabled(!controller.isCameraPreviewEnabled)
                    refresh()
                },
                if (!features.storage) null else UiDropdownItem(
                    CutsceneLang.SAVE.lang,
                    icon = "hollowengine:textures/gui/icons/save.svg",
                ) { onSave() },
                if (!features.storage) null else UiDropdownItem(
                    CutsceneLang.LOAD.lang,
                    icon = "hollowengine:textures/gui/icons/load.svg",
                ) { onLoad() },
            ),
        )
    }
}

@Composable
private fun ToolbarIcon(
    icon: String,
    id: String,
    active: Boolean = false,
    tooltip: String? = null,
    onClick: () -> Unit,
) {
    var hovered by remember { mutableStateOf(false) }
    val background = when {
        active -> TimelineAccentSoft
        hovered -> TimelineColors.PanelAlt
        else -> UiColor.Transparent
    }
    Box(
        id = id,
        mode = UiBoxMode.STACK,
        modifier = Modifier.size(24.px, 24.px).background(background)
            .border(1.px, if (active) TimelineColors.Accent else TimelineColors.Border, 4f).cursor(UiCursorShape.HAND)
            .let { if (tooltip == null) it else it.tooltipOnHover(tooltip) }.onEnter { hovered = true }
            .onExit { hovered = false }.onClick { event ->
                onClick()
                event.consume()
            },
    ) {
        key(icon) {
            Image(
                icon,
                modifier = Modifier.size(14.px, 14.px).align(UiAlign.CENTER, UiAlign.CENTER)
                    .opacity(if (active || hovered) 1f else 0.8f),
            )
        }
    }
}

private val TimelineAccentSoft = UiColor(1f, 0.54f, 0.18f, 0.22f)

@Composable
internal fun TimelineHeaders(
    controller: TimelineController,
    rows: List<TimelineRow>,
    scroll: UiScrollHandle,
    verticalOffset: Float,
    ownsVerticalScroll: Boolean,
    contentHeight: Float,
    onPropertySettings: (AnimProperty<*>) -> Unit,
    refresh: () -> Unit,
) {
    val width = controller.headerWidth
    val labelWidth = maxOf(width, rows.maxOfOrNull { headerLabelWidth(it) } ?: width)
    Column(
        id = "timeline-headers",
        modifier = Modifier.size(width.px, 100.percent).background(TimelineColors.Panel),
    ) {
        // Corner cell, level with the ruler so the rows below line up with the lanes.
        Box(
            mode = UiBoxMode.STACK,
            modifier = Modifier.size(width.px, TimelineRulerHeight.px).background(TimelineColors.Group),
        ) {
            Text(
                CutsceneLang.TRACKS.lang,
                modifier = Modifier.align(vertical = UiAlign.CENTER).margin(12.px, 0.px, 0.px, 0.px).fontSize(10f)
                    .foreground(TimelineColors.Muted),
            )
            Box(
                modifier = Modifier.position(0.px, (TimelineRulerHeight - 1f).px).size(width.px, 1.px)
                    .background(TimelineColors.Border),
            )
        }
        val overflows = labelWidth > width + 1f
        Box(
            modifier = Modifier.size(width.px, 0.px).grow(1f).clip().scrollable(
                    state = scroll,
                    vertical = ownsVerticalScroll,
                    horizontal = overflows,
                    hasHorizontalScrollbar = overflows,
                ),
        ) {
            Box(
                modifier = Modifier.position((-scroll.offsetX).px, (-verticalOffset).px)
                    .size(labelWidth.px, contentHeight.coerceAtLeast(1f).px),
            ) {
                rows.forEach { row ->
                    key(row.id) {
                        TimelineHeaderRow(row, controller, labelWidth, onPropertySettings, refresh)
                    }
                }
            }
        }
    }
}

private fun headerLabelWidth(row: TimelineRow): Float =
    8f + row.depth * 12f + row.label.length * 5.5f + headerControlsWidth(row)

private fun headerControlsWidth(row: TimelineRow): Float = when (row.kind) {
    TimelineRowKind.CHANNEL -> 26f
    else -> 82f
}

private const val EyeOnIcon = "hollowengine:textures/gui/icons/visible.svg"
private const val EyeOffIcon = "hollowengine:textures/gui/icons/invisible.svg"
private const val LockedIcon = "hollowengine:textures/gui/icons/locked.svg"
private const val UnlockedIcon = "hollowengine:textures/gui/icons/unlocked.svg"
private const val CollapseArrowIcon = "hollowengine:textures/gui/icons/arrow.svg"
private const val HideTrackIcon = "hollowengine:textures/gui/icons/cross.svg"

@Composable
private fun TimelineHeaderRow(
    row: TimelineRow,
    controller: TimelineController,
    width: Float,
    onPropertySettings: (AnimProperty<*>) -> Unit,
    refresh: () -> Unit,
) {
    val top = row.y - TimelineRulerHeight
    val isCurveView = controller.viewMode == TimelineViewMode.CURVES
    val rowCurves = if (isCurveView) row.curves.filter { it.spec.supportsCurveEditor } else row.curves
    val isFocused = isCurveView && rowCurves.isNotEmpty() && rowCurves.all { controller.isFocused(it) }
    val color = when {
        row.kind == TimelineRowKind.GROUP -> TimelineColors.Group
        isFocused -> TimelineColors.Accent.withAlpha(0.26f)
        row.locked -> UiColor(0.09f, 0.09f, 0.1f, 1f)
        !row.visible -> UiColor(0.09f, 0.1f, 0.11f, 1f)
        row.kind == TimelineRowKind.CHANNEL -> TimelineColors.PanelAlt
        else -> TimelineColors.Panel
    }
    Box(
        id = "header-${row.id}",
        mode = UiBoxMode.STACK,
        modifier = Modifier.position(0.px, top.px).size(width.px, row.height.px).background(color).onClick { event ->
                // In the graph a click picks what the graph shows; ctrl adds to it. Everywhere else
                // the row list is what it always was.
                if (isCurveView && rowCurves.isNotEmpty()) {
                    controller.focusCurves(rowCurves, additive = event.modifiers and GLFW.GLFW_MOD_CONTROL != 0)
                }
                when {
                    row.group != null -> row.group.isCollapsed = !row.group.isCollapsed
                    else -> if (!isCurveView) controller.clearSelection()
                }
                event.consume()
                refresh()
            },
    ) {
        Row(
            modifier = Modifier.size(100.percent, 100.percent).alignItems(vertical = UiAlign.CENTER)
                .padding(0.px, 0.px, 6.px, 0.px).gap(2.px),
        ) {
            Box(modifier = Modifier.size((8f + row.depth * 12f).px, 1.px))
            when {
                row.group != null -> Image(
                    CollapseArrowIcon,
                    modifier = Modifier.size(10.px, 10.px).align(vertical = UiAlign.CENTER)
                        .rotate(z = if (row.group.isCollapsed) 0f else 90f).opacity(0.75f)
                        .margin(2.px, 0.px, 4.px, 0.px),
                )

                row.kind == TimelineRowKind.PROPERTY && (row.property?.curves?.size ?: 0) > 1 ->
                    Expander(row.property?.isExpanded == true) {
                        row.property?.let { it.isExpanded = !it.isExpanded }
                        refresh()
                    }
            }
            row.color?.let { swatch ->
                Box(
                    modifier = Modifier.size(3.px, 12.px).align(vertical = UiAlign.CENTER)
                        .background(swatch.toUiColor(if (row.visible) 1f else 0.35f)).borderRadius(1.5f)
                        .margin(0.px, 0.px, 6.px, 0.px),
                )
            }
            Text(
                row.label,
                modifier = Modifier.grow(1f).align(vertical = UiAlign.CENTER)
                    .fontSize(if (row.kind == TimelineRowKind.CHANNEL) 9f else 10f)
                    .foreground(if (row.visible) TimelineColors.Text else TimelineColors.Muted).textWrap(false),
            )
            HeaderControls(row, controller, onPropertySettings, refresh)
        }
        Box(
            modifier = Modifier.position(0.px, (row.height - 1f).px).size(width.px, 1.px)
                .background(TimelineColors.Border),
        )
    }
}

@Composable
private fun HeaderControls(
    row: TimelineRow,
    controller: TimelineController,
    onPropertySettings: (AnimProperty<*>) -> Unit,
    refresh: () -> Unit,
) {
    val hideProperty = controller.onHideProperty
    val hideGroup = controller.onHideGroup
    if (row.kind == TimelineRowKind.PROPERTY && hideProperty != null) {
        row.property?.let { property ->
            HeaderIconToggle(HideTrackIcon, active = false, accent = false) {
                hideProperty(property)
                refresh()
            }
        }
    }
    if (row.kind == TimelineRowKind.GROUP && hideGroup != null) {
        row.group?.let { group ->
            HeaderIconToggle(HideTrackIcon, active = false, accent = false) {
                hideGroup(group)
                refresh()
            }
        }
    }

    when (row.kind) {
        TimelineRowKind.CHANNEL -> {
            val curve = row.curve ?: return
            HeaderIconToggle(if (curve.isVisible) EyeOnIcon else EyeOffIcon, curve.isVisible, accent = false) {
                curve.isVisible = !curve.isVisible
                refresh()
            }
        }

        TimelineRowKind.PROPERTY -> {
            val property = row.property ?: return
            HeaderIconToggle(SettingsIcon, active = false, accent = false) { onPropertySettings(property) }
            HeaderIconToggle(
                if (property.isVisible) EyeOnIcon else EyeOffIcon,
                property.isVisible,
                accent = false,
            ) {
                property.isVisible = !property.isVisible
                refresh()
            }
            HeaderIconToggle(
                if (property.isLocked) LockedIcon else UnlockedIcon,
                property.isLocked,
                accent = true,
            ) {
                property.isLocked = !property.isLocked
                refresh()
            }
        }

        TimelineRowKind.GROUP -> {
            val group = row.group ?: return
            HeaderIconToggle(if (group.isVisible) EyeOnIcon else EyeOffIcon, group.isVisible, accent = false) {
                group.isVisible = !group.isVisible
                refresh()
            }
            HeaderIconToggle(if (group.isLocked) LockedIcon else UnlockedIcon, group.isLocked, accent = true) {
                group.isLocked = !group.isLocked
                refresh()
            }
        }
    }
}

@Composable
private fun Expander(expanded: Boolean, onClick: () -> Unit) {
    Box(
        mode = UiBoxMode.STACK,
        modifier = Modifier.size(12.px, 12.px).align(vertical = UiAlign.CENTER).cursor(UiCursorShape.HAND)
            .margin(0.px, 0.px, 2.px, 0.px).onClick {
                onClick()
                it.consume()
            },
    ) {
        Image(
            CollapseArrowIcon,
            modifier = Modifier.size(9.px, 9.px).align(UiAlign.CENTER, UiAlign.CENTER)
                .rotate(z = if (expanded) 90f else 0f).opacity(0.6f),
        )
    }
}

@Composable
private fun HeaderIconToggle(icon: String, active: Boolean, accent: Boolean, onClick: () -> Unit) {
    var hovered by remember { mutableStateOf(false) }
    val tint = when {
        active && accent -> TimelineColors.Accent
        active -> TimelineColors.Text
        else -> TimelineColors.Muted
    }
    Box(
        mode = UiBoxMode.STACK,
        modifier = Modifier.size(18.px, 18.px).align(vertical = UiAlign.CENTER)
            .background(if (hovered) TimelineColors.PanelAlt else UiColor.Transparent).borderRadius(3f)
            .cursor(UiCursorShape.HAND).onEnter { hovered = true }.onExit { hovered = false }.onClick {
                onClick()
                it.consume()
            },
    ) {
        Image(
            icon,
            modifier = Modifier.size(12.px, 12.px).align(UiAlign.CENTER, UiAlign.CENTER).tint(tint)
                .opacity(if (active || hovered) 1f else 0.75f),
        )
    }
}

@Composable
internal fun ToolbarButton(label: String, id: String, color: UiColor = TimelineColors.PanelAlt, onClick: () -> Unit) {
    Box(
        id = id,
        modifier = Modifier.size(UiLength.Auto, 22.px).background(color).border(1.px, TimelineColors.Border, 3f)
            .padding(10.px, 0.px).cursor(UiCursorShape.HAND).onClick { event ->
                onClick()
                event.consume()
            },
    ) {
        Text(
            label,
            modifier = Modifier.align(UiAlign.CENTER, UiAlign.CENTER).fontSize(10f).foreground(TimelineColors.Text)
                .textWrap(false).textOverflow(UiTextOverflow.DOTS).textAlign(UiTextAlign.CENTER),
        )
    }
}

@Composable
private fun TimelineSeparator() {
    Box(modifier = Modifier.size(1.px, 18.px).background(TimelineColors.Border))
}

internal fun formatSeconds(value: Float): String = "%.2f".format(value).replace(',', '.')

/** Zoom keeping the visible centre roughly fixed; used by the toolbar buttons. */
internal fun zoomAroundCenter(controller: TimelineController, factor: Float) {
    controller.pixelsPerSecond = (controller.pixelsPerSecond * factor).coerceIn(TimelineMinZoom, TimelineMaxZoom)
}

internal const val TimelineZoomButtonFactor = 1.25f
