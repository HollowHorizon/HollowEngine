package ru.hollowhorizon.hollowengine.client.gui.timeline.ui

import androidx.compose.runtime.Composable
import ru.hollowhorizon.hollowengine.client.gui.timeline.TimelineController
import ru.hollowhorizon.hollowengine.client.ui.Box
import ru.hollowhorizon.hollowengine.client.ui.Modifier
import ru.hollowhorizon.hollowengine.client.ui.Row
import ru.hollowhorizon.hollowengine.client.ui.Text
import ru.hollowhorizon.hollowengine.client.ui.UiAlign
import ru.hollowhorizon.hollowengine.client.ui.UiColor
import ru.hollowhorizon.hollowengine.client.ui.UiCursorShape
import ru.hollowhorizon.hollowengine.client.ui.UiTextAlign
import ru.hollowhorizon.hollowengine.client.ui.percent
import ru.hollowhorizon.hollowengine.client.ui.px

@Composable
internal fun TimelineToolbar(controller: TimelineController, onCapture: () -> Unit, refresh: () -> Unit) {
    Row(
        id = "cutscene-timeline-toolbar",
        modifier = Modifier.then(
            Modifier.size(100.percent, 34.px),
            Modifier.alignItems(vertical = UiAlign.CENTER),
            Modifier.background(TimelineColors.Panel),
            Modifier.border(1.px, TimelineColors.Border),
            Modifier.padding(8.px, 0.px),
            Modifier.gap(6.px),
        ),
    ) {
        ToolbarButton("Start", "timeline-start") {
            controller.isPlaying.set(false)
            controller.setCurrentTime(0f)
            refresh()
        }
        ToolbarButton(if (controller.isPlaying.value) "Pause" else "Play", "timeline-play") {
            controller.togglePlayback()
            refresh()
        }
        ToolbarButton("End", "timeline-end") {
            controller.setCurrentTime(controller.workAreaEnd.value)
            refresh()
        }
        TimelineSeparator()
        Text(
            "Time ${"%.2f".format(controller.currentTime.value).replace(',', '.')} / ${"%.2f".format(controller.workAreaEnd.value).replace(',', '.')}",
            modifier = Modifier.then(Modifier.fontSize(11f), Modifier.foreground(TimelineColors.Text)),
        )
        TimelineSeparator()
        ToolbarButton("Capture", "timeline-capture") {
            onCapture()
            refresh()
        }
        ToolbarButton("Delete", "timeline-delete", TimelineColors.Danger) {
            controller.deleteSelectedKeyframes()
            refresh()
        }
        TimelineSeparator()
        ToolbarButton(if (controller.isCameraPreviewEnabled.value) "Preview On" else "Preview Off", "timeline-preview") {
            controller.setCameraPreviewEnabled(!controller.isCameraPreviewEnabled.value)
            refresh()
        }
        Box(modifier = Modifier.then(Modifier.size(0.px, 1.px), Modifier.grow(1f)))
        ToolbarButton("Zoom -", "timeline-zoom-out") {
            controller.pixelsPerSecond.set((controller.pixelsPerSecond.value * 0.8f).coerceAtLeast(TimelineMinZoom))
            refresh()
        }
        ToolbarButton("Zoom +", "timeline-zoom-in") {
            controller.pixelsPerSecond.set((controller.pixelsPerSecond.value * 1.25f).coerceAtMost(TimelineMaxZoom))
            refresh()
        }
    }
}

@Composable
internal fun TimelineHeaderList(controller: TimelineController, refresh: () -> Unit) {
    val rows = timelineRows(controller.groups)
    val contentHeight = (TimelineRulerHeight + rows.sumOf { it.height.toDouble() }).toFloat()
    Box(
        id = "timeline-header-scroll",
        modifier = Modifier.then(
            Modifier.size(TimelineHeaderWidth.px, 100.percent),
            Modifier.background(TimelineColors.Panel),
            Modifier.input(scrollable = true),
            Modifier.clip(),
        ),
    ) {
        Box(modifier = Modifier.size(TimelineHeaderWidth.px, contentHeight.px)) {
            Text(
                "Tracks",
                id = "timeline-header-title",
                modifier = Modifier.then(
                    Modifier.position(12.px, 8.px),
                    Modifier.fontSize(10f),
                    Modifier.foreground(TimelineColors.Muted),
                ),
            )
            rows.forEach { row ->
                TimelineHeaderRow(row, controller, refresh)
            }
        }
    }
}

@Composable
private fun TimelineHeaderRow(row: TimelineRow, controller: TimelineController, refresh: () -> Unit) {
    val color = when {
        row.kind == TimelineRowKind.GROUP -> TimelineColors.Group
        row.locked -> UiColor(0.09f, 0.09f, 0.1f, 1f)
        !row.visible -> UiColor(0.09f, 0.1f, 0.11f, 1f)
        else -> TimelineColors.Panel
    }
    Row(
        id = "header-${row.id}",
        modifier = Modifier.then(
            Modifier.position(0.px, row.y.px),
            Modifier.size(TimelineHeaderWidth.px, row.height.px),
            Modifier.alignItems(vertical = UiAlign.CENTER),
            Modifier.background(color),
            Modifier.border(1.px, TimelineColors.Border),
            Modifier.input(clickable = true),
            Modifier.onClick {
                if (row.group != null) {
                    row.group.isCollapsed.set(!row.group.isCollapsed.value)
                } else {
                    controller.clearSelection()
                }
                it.consume()
                refresh()
            },
        ),
    ) {
        Box(modifier = Modifier.size((12f + row.depth * 14f).px, 1.px))
        if (row.kind == TimelineRowKind.GROUP) {
            Text(if (row.group?.isCollapsed?.value == true) ">" else "v", modifier = Modifier.then(Modifier.size(14.px, 14.px), Modifier.fontSize(10f), Modifier.foreground(TimelineColors.Muted), Modifier.textAlign(UiTextAlign.CENTER)))
        }
        if (row.track != null) {
            Box(
                modifier = Modifier.then(
                    Modifier.size(4.px, 18.px),
                    Modifier.background(row.track.color.toUiColor(if (row.visible) 1f else 0.35f)),
                    Modifier.margin(0.px, 0.px, 8.px, 0.px),
                ),
            )
        }
        Text(
            row.label,
            modifier = Modifier.then(
                Modifier.size(0.px, 18.px),
                Modifier.grow(1f),
                Modifier.fontSize(11f),
                Modifier.foreground(if (row.visible) TimelineColors.Text else TimelineColors.Muted),
                Modifier.textWrap(false),
            ),
        )
        row.track?.let { track ->
            TrackToggle("V", track.isVisible.value, refresh) { track.isVisible.set(it) }
            TrackToggle("L", track.isLocked.value, refresh) { track.isLocked.set(it) }
        }
    }
}

@Composable
internal fun ToolbarButton(label: String, id: String, color: UiColor = TimelineColors.PanelAlt, onClick: () -> Unit) {
    Box(
        id = id,
        modifier = Modifier.then(
            Modifier.size(72.px, 22.px),
            Modifier.background(color),
            Modifier.border(1.px, TimelineColors.Border, 3f),
            Modifier.input(hoverable = true, clickable = true),
            Modifier.cursor(UiCursorShape.HAND),
            Modifier.onClick { event ->
                onClick()
                event.consume()
            },
        ),
    ) {
        Text(label, modifier = Modifier.then(Modifier.align(UiAlign.CENTER, UiAlign.CENTER), Modifier.fontSize(10f), Modifier.foreground(TimelineColors.Text), Modifier.textAlign(UiTextAlign.CENTER)))
    }
}

@Composable
private fun TrackToggle(label: String, active: Boolean, refresh: () -> Unit, onChange: (Boolean) -> Unit) {
    Text(
        label,
        modifier = Modifier.then(
            Modifier.size(18.px, 18.px),
            Modifier.foreground(if (active) TimelineColors.Text else TimelineColors.Muted),
            Modifier.fontSize(10f),
            Modifier.textAlign(UiTextAlign.CENTER),
            Modifier.input(clickable = true),
            Modifier.cursor(UiCursorShape.HAND),
            Modifier.onClick {
                onChange(!active)
                it.consume()
                refresh()
            },
        ),
    )
}

@Composable
private fun TimelineSeparator() {
    Box(modifier = Modifier.then(Modifier.size(1.px, 18.px), Modifier.background(TimelineColors.Border)))
}
