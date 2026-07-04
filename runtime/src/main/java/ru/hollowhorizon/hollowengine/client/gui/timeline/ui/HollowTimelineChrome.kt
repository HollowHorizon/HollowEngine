package ru.hollowhorizon.hollowengine.client.gui.timeline.ui

import androidx.compose.runtime.Composable
import ru.hollowhorizon.hollowengine.client.gui.timeline.TimelineController
import ru.hollowhorizon.hollowengine.client.ui.*

@Composable
internal fun TimelineToolbar(controller: TimelineController, onCapture: () -> Unit, refresh: () -> Unit) {
    Row(
        id = "cutscene-timeline-toolbar",
        modifier = Modifier.size(100.percent, 34.px)
            .alignItems(vertical = UiAlign.CENTER)
            .background(TimelineColors.Panel)
            .border(1.px, TimelineColors.Border)
            .padding(8.px, 0.px)
            .gap(6.px),
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
            "Time ${
                "%.2f".format(controller.currentTime.value).replace(',', '.')
            } / ${"%.2f".format(controller.workAreaEnd.value).replace(',', '.')}",
            modifier = Modifier.fontSize(11f).foreground(TimelineColors.Text)
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
        ToolbarButton(
            if (controller.isCameraPreviewEnabled.value) "Preview On" else "Preview Off",
            "timeline-preview"
        ) {
            controller.setCameraPreviewEnabled(!controller.isCameraPreviewEnabled.value)
            refresh()
        }
        Box(modifier = Modifier.size(0.px, 1.px).grow(1f))
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
        modifier = Modifier.size(TimelineHeaderWidth.px, 100.percent)
            .background(TimelineColors.Panel)
            .scroll(vertical = true, horizontal = true)
            .clip(),
    ) {
        Box(modifier = Modifier.size(TimelineHeaderWidth.px, contentHeight.px)) {
            Text(
                "Tracks",
                id = "timeline-header-title",
                modifier = Modifier.position(12.px, 8.px)
                    .fontSize(10f)
                    .foreground(TimelineColors.Muted)
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
        modifier = Modifier.position(0.px, row.y.px)
            .size(TimelineHeaderWidth.px, row.height.px)
            .alignItems(vertical = UiAlign.CENTER)
            .background(color)
            .border(1.px, TimelineColors.Border)
            .input(clickable = true)
            .onClick {
                if (row.group != null) {
                    row.group.isCollapsed.set(!row.group.isCollapsed.value)
                } else {
                    controller.clearSelection()
                }
                it.consume()
                refresh()
            }
    ) {
        Box(modifier = Modifier.size((12f + row.depth * 14f).px, 1.px))
        if (row.kind == TimelineRowKind.GROUP) {
            Text(
                if (row.group?.isCollapsed?.value == true) ">" else "v",
                modifier = Modifier.size(14.px, 14.px)
                    .fontSize(10f)
                    .foreground(TimelineColors.Muted)
                    .textAlign(UiTextAlign.CENTER)
            )
        }
        if (row.track != null) {
            Box(
                modifier = Modifier
                    .size(4.px, 18.px)
                    .background(row.track.color.toUiColor(if (row.visible) 1f else 0.35f))
                    .margin(0.px, 0.px, 8.px, 0.px)
            )
        }
        Text(
            row.label,
            modifier = Modifier.size(0.px, 18.px)
                .grow(1f)
                .fontSize(11f)
                .foreground(if (row.visible) TimelineColors.Text else TimelineColors.Muted)
                .textWrap(false)
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
        modifier = Modifier.size(72.px, 22.px)
            .background(color)
            .border(1.px, TimelineColors.Border, 3f)
            .input(hoverable = true, clickable = true)
            .cursor(UiCursorShape.HAND)
            .onClick { event ->
                onClick()
                event.consume()
            }
    ) {
        Text(
            label,
            modifier = Modifier.align(UiAlign.CENTER, UiAlign.CENTER)
                .fontSize(10f)
                .foreground(TimelineColors.Text)
                .textAlign(UiTextAlign.CENTER)
        )
    }
}

@Composable
private fun TrackToggle(label: String, active: Boolean, refresh: () -> Unit, onChange: (Boolean) -> Unit) {
    Text(
        label,
        modifier = Modifier.size(18.px, 18.px)
            .foreground(if (active) TimelineColors.Text else TimelineColors.Muted)
            .fontSize(10f)
            .textAlign(UiTextAlign.CENTER)
            .input(clickable = true)
            .cursor(UiCursorShape.HAND)
            .onClick {
                onChange(!active)
                it.consume()
                refresh()
            },
    )
}

@Composable
private fun TimelineSeparator() {
    Box(modifier = Modifier.size(1.px, 18.px).background(TimelineColors.Border))
}
