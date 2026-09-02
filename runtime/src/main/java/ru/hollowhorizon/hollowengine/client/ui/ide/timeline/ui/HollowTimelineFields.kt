package ru.hollowhorizon.hollowengine.client.ui.ide.timeline.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import ru.hollowhorizon.hollowengine.client.ui.*
import ru.hollowhorizon.hollowengine.client.ui.ide.timeline.CurvePreset
import ru.hollowhorizon.hollowengine.client.ui.ide.timeline.KeyInterpolation
import ru.hollowhorizon.hollowengine.client.ui.ide.timeline.RotationMode
import ru.hollowhorizon.hollowengine.client.ui.shape.GenericShape
import ru.hollowhorizon.hollowengine.client.ui.style.UiPaint
import ru.hollowhorizon.hollowengine.client.ui.widgets.UiTextInputFilter
import ru.hollowhorizon.hollowengine.client.ui.widgets.tooltipOnHover
import ru.hollowhorizon.hollowengine.client.utils.lang

// TODO: Remove hardcoding
private object FieldMetrics {
    val TitleSize = 11f
    val LabelSize = 10f
    val SectionPadding = 8.px
    val SectionGap = 6.px
    val RowGap = 6.px
    val RowPadding = 3.px
    val ControlPadding = 5.px
    val Radius = 3f
    val SectionRadius = 4f
}

@Composable
internal fun Section(title: String, id: String? = null, content: @Composable () -> Unit) {
    Column(
        id = id,
        modifier = Modifier.size(100.percent, UiLength.Fit).background(TimelineColors.PanelAlt)
            .border(1.px, TimelineColors.Border, FieldMetrics.SectionRadius).padding(FieldMetrics.SectionPadding)
            .gap(FieldMetrics.SectionGap)
    ) {
        Text(title, modifier = Modifier.fontSize(FieldMetrics.TitleSize).foreground(TimelineColors.Blue))
        content()
    }
}

@Composable
private fun FieldRow(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.size(100.percent, UiLength.Fit).padding(0.px, FieldMetrics.RowPadding)
            .gap(FieldMetrics.RowGap).alignItems(vertical = UiAlign.CENTER),
    ) {
        content()
    }
}

@Composable
private fun FieldLabel(label: String) {
    if (label.isEmpty()) return
    Text(
        label,
        modifier = Modifier.size(UiLength.Fit, UiLength.Fit).fontSize(FieldMetrics.LabelSize)
            .foreground(TimelineColors.Muted).textWrap(false),
    )
}

@Composable
internal fun PropertyLine(label: String, value: String) {
    FieldRow {
        FieldLabel(label)
        Box(
            modifier = Modifier.size(0.px, UiLength.Fit).grow(1f)
                .scrollable(vertical = false, horizontal = true, hasVerticalScrollbar = false).tooltipOnHover(value),
        ) {
            Text(
                value,
                modifier = Modifier.size(UiLength.Fit, UiLength.Fit).align(horizontal = UiAlign.END)
                    .fontSize(FieldMetrics.LabelSize).foreground(TimelineColors.Text).textWrap(false),
            )
        }
    }
}

@Composable
internal fun FloatField(
    label: String,
    value: Float,
    min: Float = Float.MIN_VALUE,
    max: Float = Float.MAX_VALUE,
    onChange: (Float) -> Unit,
) {
    val formatted = "%.3f".format(value).replace(',', '.')
    val text = remember(label, formatted) { mutableStateOf(formatted) }
    FieldRow {
        FieldLabel(label)
        TextField(
            value = text.value,
            filter = UiTextInputFilter.DECIMAL,
            onChange = { input ->
                text.value = input
                input.replace(',', '.').toFloatOrNull()?.let { parsed ->
                    onChange(parsed.coerceIn(min, max))
                }
            },
            modifier = Modifier.size(0.px, UiLength.Fit).grow(1f).background(TimelineColors.Background)
                .border(1.px, TimelineColors.Border, FieldMetrics.Radius)
                .padding(FieldMetrics.ControlPadding, FieldMetrics.RowPadding).foreground(TimelineColors.Text)
                .fontSize(FieldMetrics.LabelSize).textAlign(UiTextAlign.RIGHT),
        )
    }
}

@Composable
internal fun TogglePill(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(UiLength.Fit, UiLength.Fit)
            .background(if (active) TimelineColors.Blue else TimelineColors.Background)
            .padding(FieldMetrics.RowGap, FieldMetrics.RowPadding)
            .border(1.px, if (active) UiColor.White else TimelineColors.Border, FieldMetrics.SectionRadius)
            .cursor(UiCursorShape.HAND).onClick {
                onClick()
                it.consume()
            }) {
        Text(
            label,
            modifier = Modifier.align(UiAlign.CENTER, UiAlign.CENTER).fontSize(FieldMetrics.LabelSize)
                .foreground(TimelineColors.Text).textWrap(false),
        )
    }
}

@Composable
internal fun PillFlow(id: String? = null, content: HollowUiContent) {
    Layout(
        id = id,
        content = content,
        modifier = Modifier.size(100.percent, UiLength.Fit).gap(FieldMetrics.RowPadding)
            .lineSpacing(FieldMetrics.RowPadding.value).textWrap(),
        measurePolicy = UiMeasurePolicies.InlineFlow,
    )
}

@Composable
internal fun Pill(id: String, label: String, active: Boolean, onClick: () -> Unit) {
    InlineWidget(
        id = id,
        modifier = Modifier.background(if (active) TimelineColors.Blue else TimelineColors.Background)
            .border(1.px, if (active) UiColor.White else TimelineColors.Border, FieldMetrics.SectionRadius)
            .padding(FieldMetrics.SectionPadding, FieldMetrics.RowPadding).cursor(UiCursorShape.HAND).onClick {
                onClick()
                it.consume()
            },
    ) {
        Text(
            label,
            modifier = Modifier.fontSize(FieldMetrics.LabelSize).foreground(TimelineColors.Text).textWrap(false),
        )
    }
}

internal fun rotationModeLabel(mode: RotationMode): String = when (mode) {
    RotationMode.EULER -> CutsceneLang.ROTATION_EULER.lang
    RotationMode.QUATERNION -> CutsceneLang.ROTATION_QUATERNION.lang
}

@Composable
internal fun CurvePreview(preset: CurvePreset) {
    Box(
        id = "curve-preview-${preset.id}",
        modifier = Modifier.size(100.percent, UiLength.Fit).minSize(height = 46.px)
            .background(TimelineColors.Background).border(1.px, TimelineColors.Border, 3f).drawBehind(key = preset.id) {
                val inset = 6f
                val width = (size.width - inset * 2f).coerceAtLeast(1f)
                val height = (size.height - inset * 2f).coerceAtLeast(1f)
                fun pointX(t: Float) = inset + t * width
                fun pointY(v: Float) = inset + (1f - v) * height
                val shape = GenericShape {
                    moveTo(pointX(0f), pointY(0f))
                    when (preset.interpolation) {
                        KeyInterpolation.CONSTANT -> {
                            lineTo(pointX(1f), pointY(0f))
                            lineTo(pointX(1f), pointY(1f))
                        }

                        KeyInterpolation.LINEAR -> lineTo(pointX(1f), pointY(1f))

                        KeyInterpolation.BEZIER -> curveTo(
                            pointX(preset.outX), pointY(preset.outY),
                            pointX(preset.inX), pointY(preset.inY),
                            pointX(1f), pointY(1f),
                        )
                    }
                }
                drawShape(shape, UiPaint.Color(TimelineColors.Accent), UiDrawStyle.Stroke(1.5f))
            },
    )
}
