package ru.hollowhorizon.hollowengine.client.ui.ide.files.shadergraph

import androidx.compose.runtime.*
import org.lwjgl.glfw.GLFW
import ru.hollowhorizon.hollowengine.client.shadergraph.ShaderGraphNode
import ru.hollowhorizon.hollowengine.client.shadergraph.ShaderPinSpec
import ru.hollowhorizon.hollowengine.client.ui.*
import ru.hollowhorizon.hollowengine.client.ui.layout.UiRect
import ru.hollowhorizon.hollowengine.client.ui.style.UiPaint
import ru.hollowhorizon.hollowengine.client.ui.widgets.ColorPicker
import ru.hollowhorizon.hollowengine.client.ui.widgets.UiTextInputFilter
import java.util.*
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

/**
 * The value of an unlinked input: its name, then a field per component, or for a color a swatch that
 * opens a picker.
 */
@Composable
internal fun ValueFields(
    node: ShaderGraphNode,
    pin: ShaderPinSpec,
    values: List<Float>,
    components: Int,
    actions: ShaderFieldActions,
) {
    val shown = (0 until components).map { values.getOrElse(it) { values.lastOrNull() ?: 0f } }
    fun set(component: Int, value: Float) =
        actions.setValue(node.id, pin.name, shown.toMutableList().also { it[component] = value })

    Text(pin.name, tags = listOf("sg-pin-label"), modifier = Modifier.size(0.px, UiLength.Auto).grow(1f))
    if (pin.color && components >= 3) {
        ColorSwatch("sg-color-${node.id}-${pin.name}", shown, actions) { actions.setValue(node.id, pin.name, it) }
        return
    }
    Row(tags = listOf("sg-fields"), modifier = Modifier.size(FieldsWidth.px, UiLength.Auto)) {
        shown.forEachIndexed { component, value ->
            NumberField("sg-field-${node.id}-${pin.name}-$component", value, actions) { set(component, it) }
        }
    }
}

/**
 * A number shown as text that a drag sideways changes, by a hundredth per pixel, a thousandth with
 * Shift and a tenth with Ctrl, as one step back. A click without a drag types into it instead; while
 * it is typed into, the wheel steps it by a tenth, a hundredth with Shift and one with Ctrl.
 * Without [gestures], a drag leaves the steps back to whoever holds one open around the field.
 */
@Composable
internal fun NumberField(
    id: String,
    value: Float,
    actions: ShaderFieldActions,
    gestures: Boolean = true,
    onChange: (Float) -> Unit,
) {
    var editing by remember(id) { mutableStateOf(false) }
    val focus = LocalUiFocusRequester.current
    val start = remember(id) { floatArrayOf(0f) }
    // Whether the left button went down here, and whether it has moved since.
    val gesture = remember(id) { booleanArrayOf(false, false) }

    if (editing) {
        TypedNumber("$id-edit", value, onChange) { editing = false }
        return
    }
    Text(
        formatValue(value),
        id = id,
        tags = listOf("sg-field", "sg-number"),
        modifier = Modifier.size(0.px, FieldHeight.px).grow(1f)
            .input(hoverable = true, clickable = true, draggable = true).onPress { event ->
                if (event.button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return@onPress
                gesture[0] = true
                gesture[1] = false
                start[0] = value
                if (gestures) actions.beginGesture()
                event.consume()
            }.onDrag { event ->
                if (!gesture[0]) return@onDrag
                if (abs(event.dragTotalX) >= DRAG_THRESHOLD) gesture[1] = true
                if (gesture[1]) onChange(rounded(start[0] + event.dragTotalX * step(event.modifiers, DRAG_STEP)))
                event.consume()
            }.onRelease { event ->
                if (!gesture[0]) return@onRelease
                gesture[0] = false
                if (gestures) actions.endGesture()
                if (!gesture[1]) {
                    editing = true
                    focus.request("$id-edit")
                }
                event.consume()
            },
    )
}

/** The field a number is typed into; it goes back to text on Enter, on Escape and when it loses focus. */
@Composable
private fun TypedNumber(id: String, value: Float, onChange: (Float) -> Unit, onDone: () -> Unit) {
    var draft by remember(id) { mutableStateOf(formatValue(value)) }
    TextField(
        value = draft,
        id = id,
        filter = UiTextInputFilter.DECIMAL,
        fontSize = 8f,
        tags = listOf("sg-field"),
        modifier = Modifier.size(0.px, FieldHeight.px).grow(1f).onUnfocus { onDone() }.onKeyInput { input ->
                if (input.key == GLFW.GLFW_KEY_ENTER || input.key == GLFW.GLFW_KEY_KP_ENTER || input.key == GLFW.GLFW_KEY_ESCAPE) {
                    onDone()
                    input.consume()
                }
            }.onScroll { event ->
                val next = rounded(
                    (draft.toFloatOrNull() ?: value) + sign(event.rawScrollY) * step(
                        event.modifiers,
                        WHEEL_STEP
                    )
                )
                draft = formatValue(next)
                onChange(next)
                event.consume()
            },
        onChange = { text ->
            draft = text
            text.toFloatOrNull()?.let(onChange)
        },
    )
}

/**
 * A color as a swatch; a press opens a picker, with a field per channel under it for values past 1,
 * such as a bright emission. Everything done while the picker is open is one step back.
 */
@Composable
private fun ColorSwatch(id: String, values: List<Float>, actions: ShaderFieldActions, onChange: (List<Float>) -> Unit) {
    var anchor by remember(id) { mutableStateOf<UiRect?>(null) }
    val color = UiColor(values[0], values[1], values[2], values.getOrElse(3) { 1f })
    Box(
        id = id,
        tags = listOf("sg-swatch"),
        modifier = Modifier.size(FieldsWidth.px, FieldHeight.px)
            .drawBehind(key = color) { drawRect(bounds, UiPaint.Color(color.clamped()), radius = 2f) }
            .input(hoverable = true, clickable = true).onPress { event ->
                if (event.button == GLFW.GLFW_MOUSE_BUTTON_LEFT && anchor == null) {
                    actions.beginGesture()
                    anchor = UiRect(event.x, event.y, 0f, 0f)
                }
                event.consume()
            },
    )
    val open = anchor ?: return
    Popup(
        anchorBounds = open,
        id = "$id-popup",
        tags = listOf("sg-color-popup"),
        onDismiss = {
            anchor = null
            actions.endGesture()
        },
    ) {
        Column(tags = listOf("sg-color-popup-body")) {
            ColorPicker(
                value = color.clamped(),
                showAlpha = values.size == 4,
                onValueChange = { picked ->
                    onChange(
                        listOf(picked.red, picked.green, picked.blue, picked.alpha).take(values.size).map(::rounded)
                    )
                },
            )
            Row(tags = listOf("sg-fields")) {
                values.forEachIndexed { channel, value ->
                    NumberField("$id-channel-$channel", value, actions, gestures = false) { typed ->
                        onChange(values.toMutableList().also { it[channel] = typed })
                    }
                }
            }
        }
    }
}

private fun UiColor.clamped() =
    UiColor(red.coerceIn(0f, 1f), green.coerceIn(0f, 1f), blue.coerceIn(0f, 1f), alpha.coerceIn(0f, 1f))

private fun step(modifiers: Int, base: Float): Float = when {
    modifiers and GLFW.GLFW_MOD_SHIFT != 0 -> base / 10f
    modifiers and GLFW.GLFW_MOD_CONTROL != 0 -> base * 10f
    else -> base
}

internal fun formatValue(value: Float): String =
    if (value == value.toInt().toFloat()) value.toInt().toString() else "%.3f".format(Locale.ROOT, value).trimEnd('0')

private fun rounded(value: Float): Float = (value * 1000f).roundToInt() / 1000f

/** Pixels a press has to move before it counts as a drag rather than a click. */
private const val DRAG_THRESHOLD = 2f
private const val DRAG_STEP = 0.01f
private const val WHEEL_STEP = 0.1f
