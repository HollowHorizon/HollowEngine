package ru.hollowhorizon.hollowengine.client.gui.timeline.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import de.fabmax.kool.math.Vec2f
import de.fabmax.kool.math.Vec3f
import ru.hollowhorizon.hollowengine.client.gui.timeline.AnimTrack
import ru.hollowhorizon.hollowengine.client.gui.timeline.Keyframe
import ru.hollowhorizon.hollowengine.client.gui.timeline.TimelineController
import ru.hollowhorizon.hollowengine.client.gui.timeline.easingTypes
import ru.hollowhorizon.hollowengine.client.ui.Box
import ru.hollowhorizon.hollowengine.client.ui.Column
import ru.hollowhorizon.hollowengine.client.ui.Modifier
import ru.hollowhorizon.hollowengine.client.ui.Row
import ru.hollowhorizon.hollowengine.client.ui.Text
import ru.hollowhorizon.hollowengine.client.ui.TextField
import ru.hollowhorizon.hollowengine.client.ui.UiAlign
import ru.hollowhorizon.hollowengine.client.ui.UiColor
import ru.hollowhorizon.hollowengine.client.ui.UiCursorShape
import ru.hollowhorizon.hollowengine.client.ui.UiLength
import ru.hollowhorizon.hollowengine.client.ui.UiTextAlign
import ru.hollowhorizon.hollowengine.client.ui.UiTextInputFilter
import ru.hollowhorizon.hollowengine.client.ui.percent
import ru.hollowhorizon.hollowengine.client.ui.px

@Composable
internal fun HollowTimelineProperties(
    controller: TimelineController,
    modifier: Modifier,
    refresh: () -> Unit,
) {
    val tracks = controller.getAllTracks()
    val selectedKey = controller.selectedKeyframes.firstOrNull()
    val selectedTrack = selectedKey?.let { trackOf(it, tracks) }

    Column(
        id = "timeline-properties",
        modifier = Modifier.then(
            modifier,
            Modifier.background(TimelineColors.Panel),
            Modifier.border(1.px, TimelineColors.Border),
            Modifier.padding(10.px),
            Modifier.gap(8.px),
            Modifier.input(scrollable = true),
        ),
    ) {
        Text("Properties", modifier = Modifier.then(Modifier.fontSize(13f), Modifier.foreground(TimelineColors.Text)))
        PreviewSection(controller, refresh)
        when {
            selectedKey != null && selectedTrack != null -> KeyframeSection(selectedKey, selectedTrack, controller, refresh)
            controller.isWorkAreaSelected.value -> WorkAreaSection(controller, refresh)
            else -> EmptySection()
        }
    }
}

@Composable
private fun PreviewSection(controller: TimelineController, refresh: () -> Unit) {
    Section("Preview") {
        Row(modifier = Modifier.then(Modifier.size(100.percent, 24.px), Modifier.alignItems(vertical = UiAlign.CENTER), Modifier.gap(8.px))) {
            TogglePill(if (controller.isCameraPreviewEnabled.value) "Camera On" else "Camera Off", controller.isCameraPreviewEnabled.value) {
                controller.setCameraPreviewEnabled(!controller.isCameraPreviewEnabled.value)
                refresh()
            }
            Text(
                if (controller.isPlaying.value) "Playing" else "Paused",
                modifier = Modifier.then(Modifier.fontSize(10f), Modifier.foreground(TimelineColors.Muted)),
            )
        }
        PropertyLine("Current time", "%.3f s".format(controller.currentTime.value).replace(',', '.'))
        PropertyLine("Duration", "%.3f s".format(controller.workAreaEnd.value).replace(',', '.'))
    }
}

@Composable
private fun KeyframeSection(
    keyframe: Keyframe<*>,
    track: AnimTrack<*>,
    controller: TimelineController,
    refresh: () -> Unit,
) {
    Section("Keyframe") {
        FloatField("Time", keyframe.time, 0f, controller.workAreaEnd.value) { time ->
            controller.moveKeyframe(track, keyframe, time)
            refresh()
        }
        ValueEditor(keyframe, controller, refresh)
    }
    Section("Easing") {
        val active = easingTypes.firstOrNull { category -> category.variants.any { it.function == keyframe.easing } }
        easingTypes.forEach { category ->
            val selected = active == category
            TogglePill(category.name, selected) {
                val variant = category.variants.getOrNull(2) ?: category.variants.first()
                keyframe.easing = variant.function
                controller.onChanged?.invoke()
                refresh()
            }
        }
        active?.variants?.takeIf { it.size > 1 }?.forEach { variant ->
            TogglePill(variant.name, keyframe.easing == variant.function) {
                keyframe.easing = variant.function
                controller.onChanged?.invoke()
                refresh()
            }
        }
    }
    ToolbarButton("Delete key", "timeline-properties-delete", TimelineColors.Danger) {
        controller.deleteSelectedKeyframes()
        refresh()
    }
}

@Composable
private fun WorkAreaSection(controller: TimelineController, refresh: () -> Unit) {
    Section("Work Area") {
        FloatField("End", controller.workAreaEnd.value, 0.1f, Float.POSITIVE_INFINITY) { time ->
            controller.workAreaEnd.set(time.coerceAtLeast(0.1f))
            if (controller.currentTime.value > controller.workAreaEnd.value) controller.setCurrentTime(0f)
            refresh()
        }
    }
}

@Composable
private fun EmptySection() {
    Section("Selection") {
        Text(
            "No keyframe selected",
            modifier = Modifier.then(
                Modifier.size(100.percent, 22.px),
                Modifier.fontSize(11f),
                Modifier.foreground(TimelineColors.Muted),
            ),
        )
    }
}

@Composable
private fun ValueEditor(
    keyframe: Keyframe<*>,
    controller: TimelineController,
    refresh: () -> Unit,
) {
    when (val value = keyframe.value) {
        is Float -> FloatField("Value", value, -Float.MAX_VALUE, Float.MAX_VALUE) { next ->
            updateValue(keyframe, controller, next)
            refresh()
        }

        is Vec2f -> {
            FloatField("X", value.x, -Float.MAX_VALUE, Float.MAX_VALUE) { next ->
                updateValue(keyframe, controller, Vec2f(next, value.y))
                refresh()
            }
            FloatField("Y", value.y, -Float.MAX_VALUE, Float.MAX_VALUE) { next ->
                updateValue(keyframe, controller, Vec2f(value.x, next))
                refresh()
            }
        }

        is Vec3f -> {
            FloatField("X", value.x, -Float.MAX_VALUE, Float.MAX_VALUE) { next ->
                updateValue(keyframe, controller, Vec3f(next, value.y, value.z))
                refresh()
            }
            FloatField("Y", value.y, -Float.MAX_VALUE, Float.MAX_VALUE) { next ->
                updateValue(keyframe, controller, Vec3f(value.x, next, value.z))
                refresh()
            }
            FloatField("Z", value.z, -Float.MAX_VALUE, Float.MAX_VALUE) { next ->
                updateValue(keyframe, controller, Vec3f(value.x, value.y, next))
                refresh()
            }
        }

        else -> PropertyLine("Value", value.toString())
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.then(
            Modifier.size(100.percent, UiLength.Auto),
            Modifier.background(TimelineColors.PanelAlt),
            Modifier.border(1.px, TimelineColors.Border, 4f),
            Modifier.padding(8.px),
            Modifier.gap(6.px),
        ),
    ) {
        Text(title, modifier = Modifier.then(Modifier.fontSize(11f), Modifier.foreground(TimelineColors.Blue)))
        content()
    }
}

@Composable
private fun PropertyLine(label: String, value: String) {
    Row(modifier = Modifier.then(Modifier.size(100.percent, 18.px), Modifier.alignItems(vertical = UiAlign.CENTER))) {
        Text(label, modifier = Modifier.then(Modifier.size(96.px, 18.px), Modifier.fontSize(10f), Modifier.foreground(TimelineColors.Muted)))
        Text(value, modifier = Modifier.then(Modifier.size(0.px, 18.px), Modifier.grow(1f), Modifier.fontSize(10f), Modifier.foreground(TimelineColors.Text), Modifier.textAlign(UiTextAlign.RIGHT)))
    }
}

@Composable
private fun FloatField(
    label: String,
    value: Float,
    min: Float,
    max: Float,
    onChange: (Float) -> Unit,
) {
    val formatted = "%.3f".format(value).replace(',', '.')
    val text = remember(label, formatted) { mutableStateOf(formatted) }
    Row(modifier = Modifier.then(Modifier.size(100.percent, 24.px), Modifier.alignItems(vertical = UiAlign.CENTER), Modifier.gap(8.px))) {
        Text(label, modifier = Modifier.then(Modifier.size(80.px, 18.px), Modifier.fontSize(10f), Modifier.foreground(TimelineColors.Muted)))
        TextField(
            value = text.value,
            filter = UiTextInputFilter.DECIMAL,
            onChange = { input ->
                text.value = input
                input.replace(',', '.').toFloatOrNull()?.let { parsed ->
                    onChange(parsed.coerceIn(min, max))
                }
            },
            modifier = Modifier.then(
                Modifier.size(0.px, 22.px),
                Modifier.grow(1f),
                Modifier.background(TimelineColors.Background),
                Modifier.border(1.px, TimelineColors.Border, 3f),
                Modifier.padding(5.px, 2.px),
                Modifier.foreground(TimelineColors.Text),
                Modifier.fontSize(10f),
                Modifier.textAlign(UiTextAlign.RIGHT),
            ),
        )
    }
}

@Composable
private fun TogglePill(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier.then(
            Modifier.size(88.px, 22.px),
            Modifier.background(if (active) TimelineColors.Blue else TimelineColors.Background),
            Modifier.border(1.px, if (active) UiColor.White else TimelineColors.Border, 4f),
            Modifier.input(hoverable = true, clickable = true),
            Modifier.cursor(UiCursorShape.HAND),
            Modifier.onClick {
                onClick()
                it.consume()
            },
        ),
    ) {
        Text(label, modifier = Modifier.then(Modifier.align(UiAlign.CENTER, UiAlign.CENTER), Modifier.fontSize(10f), Modifier.foreground(TimelineColors.Text), Modifier.textAlign(UiTextAlign.CENTER)))
    }
}

@Suppress("UNCHECKED_CAST")
private fun <T> updateValue(
    keyframe: Keyframe<*>,
    controller: TimelineController,
    value: T,
) {
    val typedKey = keyframe as Keyframe<T>
    controller.updateSelectedValues("Edit keyframe value") {
        typedKey.value = value
    }
}
