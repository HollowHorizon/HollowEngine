package ru.hollowhorizon.hollowengine.client.ui.ide.timeline.ui

import androidx.compose.runtime.*
import ru.hollowhorizon.hollowengine.client.ui.ide.timeline.TimelineController
import ru.hollowhorizon.hollowengine.client.ui.*
import ru.hollowhorizon.hollowengine.client.ui.widgets.UiDropdown
import ru.hollowhorizon.hollowengine.client.ui.widgets.UiDropdownItem

private const val PlayIcon = "hollowengine:textures/gui/icons/play.svg"
private const val PauseIcon = "hollowengine:textures/gui/icons/pause.svg"
private const val StartIcon = "hollowengine:textures/gui/icons/step_backward.svg"
private const val EndIcon = "hollowengine:textures/gui/icons/step_forward.svg"
private const val ZoomInIcon = "hollowengine:textures/gui/icons/zoom_in.svg"
private const val ZoomOutIcon = "hollowengine:textures/gui/icons/zoom_out.svg"
private const val MenuIcon = "hollowengine:textures/gui/icons/options.svg"

@Composable
internal fun TimelineToolbar(
    controller: TimelineController,
    onCapture: () -> Unit,
    onSave: () -> Unit,
    onLoad: () -> Unit,
    refresh: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Row(
        id = "cutscene-timeline-toolbar",
        modifier = Modifier.size(100.percent, 34.px)
            .alignItems(vertical = UiAlign.CENTER)
            .background(TimelineColors.Panel)
            .border(1.px, TimelineColors.Border)
            .padding(6.px, 0.px)
            .gap(4.px),
    ) {
        // Transport
        ToolbarIcon(StartIcon, "timeline-start") {
            controller.isPlaying.set(false)
            controller.setCurrentTime(0f)
            refresh()
        }
        ToolbarIcon(
            if (controller.isPlaying.value) PauseIcon else PlayIcon,
            "timeline-play",
            active = controller.isPlaying.value,
        ) {
            controller.togglePlayback()
            refresh()
        }
        ToolbarIcon(EndIcon, "timeline-end") {
            controller.isPlaying.set(false)
            controller.setCurrentTime(controller.workAreaEnd.value)
            refresh()
        }

        TimelineSeparator()

        Text(
            "${formatSeconds(controller.currentTime.value)} / ${formatSeconds(controller.workAreaEnd.value)}",
            modifier = Modifier.align(vertical = UiAlign.CENTER).fontSize(11f).foreground(TimelineColors.Text),
        )

        // Push the trailing controls to the right edge.
        Box(modifier = Modifier.size(0.px, 1.px).grow(1f))

        ToolbarIcon("hollowengine:textures/gui/icons/pulse.svg", "timeline-capture") {
            onCapture()
            refresh()
        }

        TimelineSeparator()

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
            items = listOf(
                UiDropdownItem("Capture keyframe", icon = "hollowengine:textures/gui/icons/pulse.svg") {
                    onCapture(); refresh()
                },
                UiDropdownItem(
                    "Delete selected",
                    icon = "hollowengine:textures/gui/icons/remove.svg",
                    enabled = controller.selectedKeyframes.isNotEmpty(),
                ) {
                    controller.deleteSelectedKeyframes(); refresh()
                },
                UiDropdownItem(
                    if (controller.isCameraPreviewEnabled.value) "Camera preview: On" else "Camera preview: Off",
                    icon = "hollowengine:textures/gui/icons/film.svg",
                    closeOnClick = false,
                ) {
                    controller.setCameraPreviewEnabled(!controller.isCameraPreviewEnabled.value)
                    refresh()
                },
                UiDropdownItem("Save…", icon = "hollowengine:textures/gui/icons/save.svg") { onSave() },
                UiDropdownItem("Load…", icon = "hollowengine:textures/gui/icons/load.svg") { onLoad() },
            ),
        )
    }
}

@Composable
private fun ToolbarIcon(
    icon: String,
    id: String,
    active: Boolean = false,
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
        modifier = Modifier.size(24.px, 24.px)
            .background(background)
            .border(1.px, if (active) TimelineColors.Accent else TimelineColors.Border, 4f)
            .cursor(UiCursorShape.HAND)
            .onEnter { hovered = true }
            .onExit { hovered = false }
            .onClick { event ->
                onClick()
                event.consume()
            },
    ) {
        key(icon) {
            Image(
                icon,
                modifier = Modifier.size(14.px, 14.px)
                    .align(UiAlign.CENTER, UiAlign.CENTER)
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
    offsetY: Float,
    contentHeight: Float,
    refresh: () -> Unit,
) {
    Column(
        id = "timeline-headers",
        modifier = Modifier.size(TimelineHeaderWidth.px, 100.percent)
            .background(TimelineColors.Panel),
    ) {
        // Corner cell, level with the ruler so the rows below line up with the lanes.
        Box(
            mode = UiBoxMode.STACK,
            modifier = Modifier.size(TimelineHeaderWidth.px, TimelineRulerHeight.px).background(TimelineColors.Group),
        ) {
            Text(
                "Tracks",
                modifier = Modifier.align(vertical = UiAlign.CENTER)
                    .margin(12.px, 0.px, 0.px, 0.px)
                    .fontSize(10f)
                    .foreground(TimelineColors.Muted),
            )
            Box(
                modifier = Modifier.position(0.px, (TimelineRulerHeight - 1f).px)
                    .size(TimelineHeaderWidth.px, 1.px)
                    .background(TimelineColors.Border),
            )
        }
        Box(modifier = Modifier.size(TimelineHeaderWidth.px, 0.px).grow(1f).clip()) {
            Box(
                modifier = Modifier.position(0.px, (-offsetY).px)
                    .size(TimelineHeaderWidth.px, contentHeight.coerceAtLeast(1f).px),
            ) {
                rows.forEach { row ->
                    TimelineHeaderRow(row, controller, refresh)
                }
            }
        }
    }
}

private const val EyeOnIcon = "hollowengine:textures/gui/icons/visible.svg"
private const val EyeOffIcon = "hollowengine:textures/gui/icons/invisible.svg"
private const val LockedIcon = "hollowengine:textures/gui/icons/locked.svg"
private const val UnlockedIcon = "hollowengine:textures/gui/icons/unlocked.svg"
private const val CollapseArrowIcon = "hollowengine:textures/gui/icons/arrow.svg"

@Composable
private fun TimelineHeaderRow(row: TimelineRow, controller: TimelineController, refresh: () -> Unit) {
    val top = row.y - TimelineRulerHeight
    val isGroup = row.kind == TimelineRowKind.GROUP
    val color = when {
        isGroup -> TimelineColors.Group
        row.locked -> UiColor(0.09f, 0.09f, 0.1f, 1f)
        !row.visible -> UiColor(0.09f, 0.1f, 0.11f, 1f)
        else -> TimelineColors.Panel
    }
    Box(
        id = "header-${row.id}",
        mode = UiBoxMode.STACK,
        modifier = Modifier.position(0.px, top.px)
            .size(TimelineHeaderWidth.px, row.height.px)
            .background(color)
            .onClick {
                if (row.group != null) {
                    row.group.isCollapsed = !row.group.isCollapsed
                } else {
                    controller.clearSelection()
                }
                it.consume()
                refresh()
            },
    ) {
        Row(
            modifier = Modifier.size(100.percent, 100.percent)
                .alignItems(vertical = UiAlign.CENTER)
                .padding(0.px, 0.px, 6.px, 0.px)
                .gap(2.px),
        ) {
            Box(modifier = Modifier.size((8f + row.depth * 12f).px, 1.px))
            if (isGroup) {
                Image(
                    CollapseArrowIcon,
                    modifier = Modifier.size(10.px, 10.px)
                        .align(vertical = UiAlign.CENTER)
                        .rotate(z = if (row.group?.isCollapsed == true) 0f else 90f)
                        .opacity(0.75f)
                        .margin(2.px, 0.px, 4.px, 0.px),
                )
            }
            if (row.track != null) {
                Box(
                    modifier = Modifier.size(3.px, 16.px)
                        .align(vertical = UiAlign.CENTER)
                        .background(row.track.color.toUiColor(if (row.visible) 1f else 0.35f))
                        .borderRadius(1.5f)
                        .margin(0.px, 0.px, 6.px, 0.px),
                )
            }
            Text(
                row.label,
                modifier = Modifier.grow(1f)
                    .align(vertical = UiAlign.CENTER)
                    .fontSize(11f)
                    .foreground(if (row.visible) TimelineColors.Text else TimelineColors.Muted)
                    .textWrap(false),
            )
            row.track?.let { track ->
                HeaderIconToggle(
                    if (track.isVisible.value) EyeOnIcon else EyeOffIcon,
                    active = track.isVisible.value,
                    accent = false,
                ) {
                    track.isVisible.set(!track.isVisible.value)
                    refresh()
                }
                HeaderIconToggle(
                    if (track.isLocked.value) LockedIcon else UnlockedIcon,
                    active = track.isLocked.value,
                    accent = true,
                ) {
                    track.isLocked.set(!track.isLocked.value)
                    refresh()
                }
            }
        }
        Box(
            modifier = Modifier.position(0.px, (row.height - 1f).px)
                .size(TimelineHeaderWidth.px, 1.px)
                .background(TimelineColors.Border),
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
        modifier = Modifier.size(18.px, 18.px)
            .align(vertical = UiAlign.CENTER)
            .background(if (hovered) TimelineColors.PanelAlt else UiColor.Transparent)
            .borderRadius(3f)
            .cursor(UiCursorShape.HAND)
            .onEnter { hovered = true }
            .onExit { hovered = false }
            .onClick {
                onClick()
                it.consume()
            },
    ) {
        Image(
            icon,
            modifier = Modifier.size(12.px, 12.px)
                .align(UiAlign.CENTER, UiAlign.CENTER)
                .tint(tint)
                .opacity(if (active || hovered) 1f else 0.75f),
        )
    }
}

@Composable
internal fun ToolbarButton(label: String, id: String, color: UiColor = TimelineColors.PanelAlt, onClick: () -> Unit) {
    Box(
        id = id,
        modifier = Modifier.size(UiLength.Auto, 22.px)
            .background(color)
            .border(1.px, TimelineColors.Border, 3f)
            .padding(10.px, 0.px)
            .cursor(UiCursorShape.HAND)
            .onClick { event ->
                onClick()
                event.consume()
            },
    ) {
        Text(
            label,
            modifier = Modifier.align(UiAlign.CENTER, UiAlign.CENTER)
                .fontSize(10f)
                .foreground(TimelineColors.Text)
                .textAlign(UiTextAlign.CENTER),
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
    val next = (controller.pixelsPerSecond.value * factor).coerceIn(TimelineMinZoom, TimelineMaxZoom)
    controller.pixelsPerSecond.set(next)
}

internal const val TimelineZoomButtonFactor = 1.25f
