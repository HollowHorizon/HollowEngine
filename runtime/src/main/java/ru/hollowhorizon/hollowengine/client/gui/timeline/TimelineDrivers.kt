package ru.hollowhorizon.hollowengine.client.gui.timeline

import de.fabmax.kool.input.CursorShape
import de.fabmax.kool.input.KeyboardInput
import de.fabmax.kool.input.PointerInput
import de.fabmax.kool.math.MutableVec3f
import de.fabmax.kool.math.Vec2f
import de.fabmax.kool.math.Vec3f
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import ru.hollowhorizon.hollowengine.client.gui.colors.ColorTheme

private fun UiScope.floatField(
    value: Float,
    label: String? = null,
    width: Grow = Grow.Std,
    onValueChange: (Float) -> Unit
) {
    Row {
        modifier.alignY(AlignmentY.Center)
        if (label != null) {
            Text(label) {
                modifier
                    .alignY(AlignmentY.Center)
                    .margin(end = 4.dp)
                    .textColor(Color.WHITE.withAlpha(0.7f))
                    .font(sizes.smallText)
            }
        }

        var text by remember(value.toString())

        if (text.toFloatOrNull() != value) {
            text = value.toString()
        }

        TextField(text) {
            modifier
                .width(width)
                .height(26.dp)
                .alignY(AlignmentY.Center)
                .background(RoundRectBackground(ColorTheme.UI.BackgroundGeneral, sizes.smallGap))
                .padding(horizontal = 4.dp, vertical = 2.dp)
                .textAlignX(AlignmentX.End)
                .colors(
                    textColor = ColorTheme.UI.WhiteReplacement,
                    lineColor = Color(0f, 0f, 0f, 0f),
                    lineColorFocused = ColorTheme.UI.BackgroundAccent,
                    selectionColor = ColorTheme.UI.BackgroundAccent.withAlpha(0.3f),
                )
                .onEnter { PointerInput.cursorShape = CursorShape.RESIZE_E }
                .onExit { PointerInput.cursorShape = CursorShape.DEFAULT }
                .onDrag { event ->
                    val speed = when {
                        KeyboardInput.isCtrlDown -> 0.25f
                        KeyboardInput.isShiftDown -> 0.01f
                        else -> 0.05f
                    }
                    val newValue = value + event.pointer.delta.x * speed
                    text = "%.3f".format(newValue).replace(',', '.')
                    onValueChange(newValue)
                    event.pointer.consume()
                }
                .onChange {
                    text = it
                    it.toFloatOrNull()?.let { parsed -> onValueChange(parsed) }
                }
        }
    }
}

class FloatPropertyDriver(
    val onApply: (Float) -> Unit
) : PropertyDriver<Float> {

    override fun interpolate(start: Float, end: Float, fraction: Float): Float {
        return start + (end - start) * fraction
    }

    override fun apply(value: Float) {
        onApply(value)
    }

    override fun UiScope.drawEditor(value: Float, onChange: (Float) -> Unit) {
        Column(width = Grow.Std) {
            modifier.margin(bottom = 8.dp)
            Text("Value") { modifier.margin(bottom = 4.dp).textColor(colors.primary) }

            floatField(value, width = Grow.Std) { newValue ->
                onChange(newValue)
            }
        }
    }
}

class Vec2PropertyDriver(
    val onApply: (Vec2f) -> Unit
) : PropertyDriver<Vec2f> {

    override fun interpolate(start: Vec2f, end: Vec2f, fraction: Float): Vec2f {
        return Vec2f(
            start.x + (end.x - start.x) * fraction,
            start.y + (end.y - start.y) * fraction
        )
    }

    override fun apply(value: Vec2f) {
        onApply(value)
    }

    override fun UiScope.drawEditor(value: Vec2f, onChange: (Vec2f) -> Unit) {
        Column(width = Grow.Std) {
            modifier.margin(bottom = 8.dp)
            Text("Vector 2") { modifier.margin(bottom = 4.dp).textColor(colors.primary) }

            Row(width = Grow.Std) {
                modifier.margin(bottom = 4.dp)
                floatField(value.x, "X", width = Grow(1f)) { newX ->
                    onChange(Vec2f(newX, value.y))
                }
                Box(width = 8.dp) {}
                floatField(value.y, "Y", width = Grow(1f)) { newY ->
                    onChange(Vec2f(value.x, newY))
                }
            }
        }
    }
}

class Vec3PropertyDriver(
    val onApply: (Vec3f) -> Unit
) : PropertyDriver<Vec3f> {

    override fun interpolate(start: Vec3f, end: Vec3f, fraction: Float): Vec3f {
        return start.mix(end, fraction, MutableVec3f())
    }

    override fun apply(value: Vec3f) {
        onApply(value)
    }

    override fun UiScope.drawEditor(value: Vec3f, onChange: (Vec3f) -> Unit) {
        Column(width = Grow.Std) {
            modifier.margin(bottom = 8.dp)
            Text("Vector 3") { modifier.margin(bottom = 4.dp).textColor(colors.primary) }

            Row(width = Grow.Std) {
                modifier.margin(bottom = 4.dp)
                floatField(value.x, "X", width = Grow.Std) { newX ->
                    onChange(Vec3f(newX, value.y, value.z))
                }
            }

            Row(width = Grow.Std) {
                modifier.margin(bottom = 4.dp)
                floatField(value.y, "Y", width = Grow.Std) { newY ->
                    onChange(Vec3f(value.x, newY, value.z))
                }
            }

            Row(width = Grow.Std) {
                floatField(value.z, "Z", width = Grow.Std) { newZ ->
                    onChange(Vec3f(value.x, value.y, newZ))
                }
            }
        }
    }
}
