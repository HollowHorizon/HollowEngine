package ru.hollowhorizon.hollowengine.client.ui.widgets

import ru.hollowhorizon.hollowengine.client.ui.*
import ru.hollowhorizon.hollowengine.client.ui.layout.invalidateDraw
import ru.hollowhorizon.hollowengine.client.ui.layout.invalidateInput
import ru.hollowhorizon.hollowengine.client.ui.layout.invalidateLayout
import ru.hollowhorizon.hollowengine.client.ui.style.UiPaint
import kotlin.math.roundToInt

enum class UiCheckboxVariant {
    CHECKBOX,
    RADIO,
    SWITCH;

    companion object {
        fun from(value: String?): UiCheckboxVariant = when (value?.trim()?.lowercase()) {
            "radio", "radio-button" -> RADIO
            "switch", "toggle" -> SWITCH
            else -> CHECKBOX
        }
    }
}

enum class UiTextFieldMode {
    SINGLE_LINE,
    MULTI_LINE;

    companion object {
        fun from(value: String?): UiTextFieldMode = when (value?.trim()?.lowercase()) {
            "multi", "multiline", "multi-line", "textarea", "area" -> MULTI_LINE
            else -> SINGLE_LINE
        }
    }
}

enum class UiTextInputFilter {
    ANY,
    INTEGER,
    DECIMAL;

    fun accepts(value: String): Boolean = when (this) {
        ANY -> true
        INTEGER -> value.isEmpty() || value == "-" || value.toIntOrNull() != null
        DECIMAL -> value.isEmpty() || value == "-" || value == "." || value == "-." || value.toFloatOrNull() != null
    }

    companion object {
        fun from(value: String?): UiTextInputFilter = when (value?.trim()?.lowercase()) {
            "int", "integer", "whole" -> INTEGER
            "float", "double", "decimal", "number" -> DECIMAL
            else -> ANY
        }
    }
}

data class UiSliderStyle(
    val trackThickness: UiLength? = null,
    val trackPaint: UiPaint? = null,
    val activeTrackPaint: UiPaint? = null,
    val thumbPaint: UiPaint? = null,
    val thumbBorder: UiBorder? = null,
    val thumbSize: UiSize? = null,
    val radius: Float? = null,
) {
    fun merge(other: UiSliderStyle): UiSliderStyle = UiSliderStyle(
        trackThickness = other.trackThickness ?: trackThickness,
        trackPaint = other.trackPaint ?: trackPaint,
        activeTrackPaint = other.activeTrackPaint ?: activeTrackPaint,
        thumbPaint = other.thumbPaint ?: thumbPaint,
        thumbBorder = other.thumbBorder ?: thumbBorder,
        thumbSize = other.thumbSize ?: thumbSize,
        radius = other.radius ?: radius,
    )
}

data class UiCheckboxStyle(
    val markPaint: UiPaint? = null,
    val activePaint: UiPaint? = null,
    val variant: UiCheckboxVariant? = null,
) {
    fun merge(other: UiCheckboxStyle): UiCheckboxStyle = UiCheckboxStyle(
        markPaint = other.markPaint ?: markPaint,
        activePaint = other.activePaint ?: activePaint,
        variant = other.variant ?: variant,
    )
}

data class UiTextFieldStyle(
    val caretColor: UiColor? = null,
    val selectionColor: UiColor? = null,
    val lineNumberColor: UiColor? = null,
    val inlayHintColor: UiColor? = null,
    val lineNumbers: Boolean? = null,
    val inlayHints: Boolean? = null,
) {
    fun merge(other: UiTextFieldStyle): UiTextFieldStyle = UiTextFieldStyle(
        caretColor = other.caretColor ?: caretColor,
        selectionColor = other.selectionColor ?: selectionColor,
        lineNumberColor = other.lineNumberColor ?: lineNumberColor,
        inlayHintColor = other.inlayHintColor ?: inlayHintColor,
        lineNumbers = other.lineNumbers ?: lineNumbers,
        inlayHints = other.inlayHints ?: inlayHints,
    )
}

class SliderNode(
    value: Float = 0f,
    min: Float = 0f,
    max: Float = 1f,
    step: Float = 0f,
    id: String? = null,
    tags: Iterable<String> = emptyList(),
    modifiers: Iterable<Modifier> = emptyList(),
    attributes: Map<String, String> = emptyMap(),
) : BaseUiNode(UiSliderType, id?.trimUiIdPrefix(), tags.map { it.trimUiTagPrefix() }, modifiers, attributes),
    UiStatefulNode {
    var min: Float = min
        set(value) {
            if (field == value) return
            field = value
            this.value = this.value
            invalidateInput()
            invalidateDraw()
        }

    var max: Float = max
        set(value) {
            if (field == value) return
            field = value
            this.value = this.value
            invalidateInput()
            invalidateDraw()
        }

    var step: Float = step.coerceAtLeast(0f)
        set(value) {
            val normalized = value.coerceAtLeast(0f)
            if (field == normalized) return
            field = normalized
            this.value = this.value
            invalidateInput()
        }

    var value: Float = 0f
        set(value) {
            val normalized = normalize(value)
            if (field == normalized) return
            field = normalized
            invalidateDraw()
        }

    init {
        this.value = value
    }

    val fraction: Float
        get() {
            val range = max - min
            if (range == 0f) return 0f
            return ((value - min) / range).coerceIn(0f, 1f)
        }

    fun setFromLocalX(localX: Float, width: Float): Boolean {
        val old = value
        value = min + (max - min) * (localX / width.coerceAtLeast(1f)).coerceIn(0f, 1f)
        return old != value
    }

    override fun exportState(): UiNodePersistentState = SliderPersistentState(value)

    override fun importState(state: UiNodePersistentState) {
        if (state is SliderPersistentState) value = state.value
    }

    private fun normalize(raw: Float): Float {
        val low = minOf(min, max)
        val high = maxOf(min, max)
        val clamped = raw.coerceIn(low, high)
        if (step <= 0f) return clamped
        val stepped = min + ((clamped - min) / step).roundToInt() * step
        return stepped.coerceIn(low, high)
    }
}

class CheckboxNode(
    checked: Boolean = false,
    variant: UiCheckboxVariant = UiCheckboxVariant.CHECKBOX,
    id: String? = null,
    tags: Iterable<String> = emptyList(),
    modifiers: Iterable<Modifier> = emptyList(),
    attributes: Map<String, String> = emptyMap(),
) : BaseUiNode(UiCheckboxType, id?.trimUiIdPrefix(), tags.map { it.trimUiTagPrefix() }, modifiers, attributes),
    UiStatefulNode {
    var checked: Boolean = checked
        set(value) {
            if (field == value) return
            field = value
            if (value) states += UiState.SELECTED else states -= UiState.SELECTED
            invalidateDraw()
        }

    var variant: UiCheckboxVariant = variant
        set(value) {
            if (field == value) return
            field = value
            invalidateDraw()
        }

    init {
        this.variant = variant
        this.checked = checked
    }

    fun toggle(): Boolean {
        checked = !checked
        return checked
    }

    override fun exportState(): UiNodePersistentState = CheckboxPersistentState(checked)

    override fun importState(state: UiNodePersistentState) {
        if (state is CheckboxPersistentState) checked = state.checked
    }
}

internal fun Map<String, String>.readSliderValue(name: String, fallback: Float): Float =
    this[name]?.toFloatOrNull() ?: fallback

internal fun Map<String, String>.readBoolean(name: String, fallback: Boolean = false): Boolean =
    when (this[name]?.lowercase()) {
        "true", "yes", "1", "enabled", "checked" -> true
        "false", "no", "0", "disabled", "unchecked" -> false
        else -> fallback
    }

private fun String.trimUiIdPrefix() = removePrefix("#")

private fun String.trimUiTagPrefix() = removePrefix(".")
