package ru.hollowhorizon.hollowengine.client.gui.timeline.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import de.fabmax.kool.math.Easing
import de.fabmax.kool.math.Vec2f
import de.fabmax.kool.math.Vec3f
import ru.hollowhorizon.hollowengine.client.gui.timeline.Keyframe
import ru.hollowhorizon.hollowengine.client.gui.timeline.TimelineController
import ru.hollowhorizon.hollowengine.client.gui.timeline.easingTypes
import ru.hollowhorizon.hollowengine.client.ui.*
import ru.hollowhorizon.hollowengine.client.ui.shape.GenericShape
import ru.hollowhorizon.hollowengine.client.ui.style.UiPaint
import ru.hollowhorizon.hollowengine.client.ui.widgets.UiTextInputFilter

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
        modifier = modifier.background(TimelineColors.Panel)
            .border(1.px, TimelineColors.Border)
            .padding(10.px)
            .gap(8.px)
            .scroll(vertical = true)
    ) {
        Text("Properties", modifier = Modifier.fontSize(13f).foreground(TimelineColors.Text))
        PreviewSection(controller, refresh)
        when {
            selectedKey != null && selectedTrack != null -> KeyframeSection(
                selectedKey,
                controller,
                refresh
            )

            controller.isWorkAreaSelected.value -> WorkAreaSection(controller, refresh)
            else -> EmptySection()
        }
    }
}

@Composable
private fun PreviewSection(controller: TimelineController, refresh: () -> Unit) {
    Section("Preview") {
        Row(
            modifier = Modifier.size(100.percent, 24.px)
                .alignItems(vertical = UiAlign.CENTER)
                .gap(8.px)
        ) {
            TogglePill(
                if (controller.isCameraPreviewEnabled.value) "Camera On" else "Camera Off",
                controller.isCameraPreviewEnabled.value
            ) {
                controller.setCameraPreviewEnabled(!controller.isCameraPreviewEnabled.value)
                refresh()
            }
            Text(
                if (controller.isPlaying.value) "Playing" else "Paused",
                modifier = Modifier.fontSize(10f).foreground(TimelineColors.Muted),
            )
        }
        PropertyLine("Current time", "%.3f s".format(controller.currentTime.value).replace(',', '.'))
        PropertyLine("Duration", "%.3f s".format(controller.workAreaEnd.value).replace(',', '.'))
    }
}

@Composable
private fun KeyframeSection(
    keyframe: Keyframe<*>,
    controller: TimelineController,
    refresh: () -> Unit,
) {
    val selectionCount = controller.selectedKeyframes.size
    Section(if (selectionCount == 1) "Keyframe" else "$selectionCount Keyframes") {
        FloatField("Time", keyframe.time, 0f, controller.workAreaEnd.value) { time ->
            controller.nudgeSelectedKeyframes(snapTimelineTime(time, currentUiKeyModifiers()) - keyframe.time)
            refresh()
        }
        ValueEditor(keyframe, controller, refresh)
    }
    Section("Easing", id = "timeline-easing-section") {
        val active = easingTypes.firstOrNull { category -> category.variants.any { it.function == keyframe.easing } }
        EasingPreview(keyframe)
        EasingFlow(id = "timeline-easing-categories") {
            easingTypes.forEach { category ->
                EasingPill(category.name, active == category) {
                    val variant = category.variants.getOrNull(2) ?: category.variants.first()
                    updateSelectedEasing(controller, variant.function)
                    refresh()
                }
            }
        }
        active?.variants?.takeIf { it.size > 1 }?.let { variants ->
            Text("Curve", modifier = Modifier.fontSize(9f).foreground(TimelineColors.Muted))
            EasingFlow(id = "timeline-easing-variants") {
                variants.forEach { variant ->
                    EasingPill(variant.name, keyframe.easing == variant.function) {
                        updateSelectedEasing(controller, variant.function)
                        refresh()
                    }
                }
            }
        }
    }
    ToolbarButton(if (selectionCount == 1) "Delete key" else "Delete keys", "timeline-properties-delete", TimelineColors.Danger) {
        controller.deleteSelectedKeyframes()
        refresh()
    }
}

@Composable
private fun WorkAreaSection(controller: TimelineController, refresh: () -> Unit) {
    Section("Work Area") {
        FloatField("End", controller.workAreaEnd.value, 0.1f, Float.POSITIVE_INFINITY) { time ->
            controller.workAreaEnd.set(snapTimelineTime(time, currentUiKeyModifiers()).coerceAtLeast(0.1f))
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
            modifier = Modifier.size(100.percent, 22.px)
                .fontSize(11f)
                .foreground(TimelineColors.Muted)
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
            updateSelectedValues(controller) { current -> if (current is Float) next else current }
            refresh()
        }

        is Vec2f -> {
            FloatField("X", value.x, -Float.MAX_VALUE, Float.MAX_VALUE) { next ->
                updateSelectedValues(controller) { current ->
                    if (current is Vec2f) Vec2f(next, current.y) else current
                }
                refresh()
            }
            FloatField("Y", value.y, -Float.MAX_VALUE, Float.MAX_VALUE) { next ->
                updateSelectedValues(controller) { current ->
                    if (current is Vec2f) Vec2f(current.x, next) else current
                }
                refresh()
            }
        }

        is Vec3f -> {
            FloatField("X", value.x, -Float.MAX_VALUE, Float.MAX_VALUE) { next ->
                updateSelectedValues(controller) { current ->
                    if (current is Vec3f) Vec3f(next, current.y, current.z) else current
                }
                refresh()
            }
            FloatField("Y", value.y, -Float.MAX_VALUE, Float.MAX_VALUE) { next ->
                updateSelectedValues(controller) { current ->
                    if (current is Vec3f) Vec3f(current.x, next, current.z) else current
                }
                refresh()
            }
            FloatField("Z", value.z, -Float.MAX_VALUE, Float.MAX_VALUE) { next ->
                updateSelectedValues(controller) { current ->
                    if (current is Vec3f) Vec3f(current.x, current.y, next) else current
                }
                refresh()
            }
        }

        else -> PropertyLine("Value", value.toString())
    }
}

@Composable
private fun EasingPreview(keyframe: Keyframe<*>) {
    EasingPreview(keyframe.easing)
}

@Composable
internal fun EasingPreview(easing: Easing.Easing) {
    val curve = remember(easing) {
        GenericShape { size ->
            val samples = 64
            for (i in 0..samples) {
                val t = i / samples.toFloat()
                val eased = easing.eased(t).coerceIn(0f, 1f)
                val x = size.width * (0.08f + t * 0.84f)
                val y = size.height * (0.06f + (1f - eased) * 0.88f)
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
        }
    }
    Box(
        id = "timeline-easing-preview",
        modifier = Modifier.size(100.percent, 200.px)
            .background(TimelineColors.Background)
            .border(1.px, TimelineColors.Border, 3f)
            .clip()
            .draw(key = easing) {
                drawShape(curve, UiPaint.Color(TimelineColors.Blue), UiDrawStyle.Stroke(1.5f))
            },
    )
}

@Composable
private fun EasingFlow(id: String? = null, content: HollowUiContent) {
    Layout(
        id = id,
        content = content,
        modifier = Modifier.size(100.percent, UiLength.Fit).gap(4.px).lineSpacing(4f).textWrap(),
        measurePolicy = UiMeasurePolicies.InlineFlow,
    )
}

@Composable
private fun EasingPill(label: String, active: Boolean, onClick: () -> Unit) {
    InlineWidget(
        id = "easing-$label",
        modifier = Modifier.background(if (active) TimelineColors.Blue else TimelineColors.Background)
            .border(1.px, if (active) UiColor.White else TimelineColors.Border, 4f)
            .padding(8.px, 3.px)
            .cursor(UiCursorShape.HAND)
            .onClick {
                onClick()
                it.consume()
            },
    ) {
        Text(
            label,
            modifier = Modifier.fontSize(10f).foreground(TimelineColors.Text).textWrap(false),
        )
    }
}

@Composable
private fun Section(title: String, id: String? = null, content: @Composable () -> Unit) {
    Column(
        id = id,
        modifier = Modifier.size(100.percent, UiLength.Fit)
            .background(TimelineColors.PanelAlt)
            .border(1.px, TimelineColors.Border, 4f)
            .padding(8.px)
            .gap(6.px)
    ) {
        Text(title, modifier = Modifier.fontSize(11f).foreground(TimelineColors.Blue))
        content()
    }
}

@Composable
private fun PropertyLine(label: String, value: String) {
    Row(modifier = Modifier.size(100.percent, 18.px).alignItems(vertical = UiAlign.CENTER)) {
        Text(
            label,
            modifier = Modifier.size(96.px, 18.px)
                .fontSize(10f)
                .foreground(TimelineColors.Muted)
        )
        Text(
            value,
            modifier = Modifier.size(0.px, 18.px)
                .grow(1f)
                .fontSize(10f)
                .foreground(TimelineColors.Text)
                .textAlign(UiTextAlign.RIGHT)
        )
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
    Row(
        modifier =
            Modifier.size(100.percent, UiLength.Fit)
                .minSize(height = 24.px)
                .alignItems(vertical = UiAlign.CENTER)
                .gap(8.px)
    ) {
        Text(
            label,
            modifier =
                Modifier.size(80.px, 18.px)
                    .fontSize(10f)
                    .foreground(TimelineColors.Muted)

        )
        TextField(
            value = text.value,
            filter = UiTextInputFilter.DECIMAL,
            onChange = { input ->
                text.value = input
                input.replace(',', '.').toFloatOrNull()?.let { parsed ->
                    onChange(parsed.coerceIn(min, max))
                }
            },
            modifier = Modifier.size(0.px, 22.px)
                .grow(1f)
                .background(TimelineColors.Background)
                .border(1.px, TimelineColors.Border, 3f)
                .padding(5.px, 0.px)
                .foreground(TimelineColors.Text)
                .fontSize(10f)
                .textAlign(UiTextAlign.RIGHT),
        )
    }
}

@Composable
private fun TogglePill(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(88.px, 22.px)
            .background(if (active) TimelineColors.Blue else TimelineColors.Background)
            .border(1.px, if (active) UiColor.White else TimelineColors.Border, 4f)
            .cursor(UiCursorShape.HAND)
            .onClick {
                onClick()
                it.consume()
            }
    ) {
        Text(
            label,
            modifier =
                Modifier.align(UiAlign.CENTER, UiAlign.CENTER)
                    .fontSize(10f)
                    .foreground(TimelineColors.Text)
                    .textAlign(UiTextAlign.CENTER)

        )
    }
}

@Suppress("UNCHECKED_CAST")
private fun updateSelectedValues(controller: TimelineController, update: (Any?) -> Any?) {
    controller.updateSelectedValues("Edit keyframe value") {
        controller.selectedKeyframes.forEach { keyframe ->
            val current = keyframe.value
            val next = update(current)
            if (next !== current) (keyframe as Keyframe<Any?>).value = next
        }
    }
}

private fun updateSelectedEasing(controller: TimelineController, easing: Easing.Easing) {
    controller.updateSelectedValues("Edit keyframe easing") {
        controller.selectedKeyframes.forEach { it.easing = easing }
    }
}
