package ru.hollowhorizon.hollowengine.client.ui

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
) : BaseUiNode(UiNodeType.SLIDER.typeName, id?.trimUiIdPrefix(), tags.map { it.trimUiTagPrefix() }, modifiers, attributes),
    UiStatefulNode {
    var min: Float = min
        set(value) {
            field = value
            this.attributes["min"] = value.toString()
            this.value = this.value
        }

    var max: Float = max
        set(value) {
            field = value
            this.attributes["max"] = value.toString()
            this.value = this.value
        }

    var step: Float = step.coerceAtLeast(0f)
        set(value) {
            field = value.coerceAtLeast(0f)
            this.attributes["step"] = field.toString()
            this.value = this.value
        }

    var value: Float = 0f
        set(value) {
            field = normalize(value)
            this.attributes["value"] = field.toString()
        }

    init {
        this.value = value
        this.attributes["min"] = this.min.toString()
        this.attributes["max"] = this.max.toString()
        this.attributes["step"] = this.step.toString()
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
) : BaseUiNode(UiNodeType.CHECKBOX.typeName, id?.trimUiIdPrefix(), tags.map { it.trimUiTagPrefix() }, modifiers, attributes),
    UiStatefulNode {
    var checked: Boolean = checked
        set(value) {
            field = value
            this.attributes["checked"] = value.toString()
            if (value) states += UiState.SELECTED else states -= UiState.SELECTED
        }

    var variant: UiCheckboxVariant = variant
        set(value) {
            field = value
            this.attributes["variant"] = value.name.lowercase()
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

class TextFieldNode(
    value: String = "",
    mode: UiTextFieldMode = UiTextFieldMode.SINGLE_LINE,
    filter: UiTextInputFilter = UiTextInputFilter.ANY,
    multiCaret: Boolean = false,
    id: String? = null,
    tags: Iterable<String> = emptyList(),
    modifiers: Iterable<Modifier> = emptyList(),
    attributes: Map<String, String> = emptyMap(),
) : BaseUiNode(UiNodeType.TEXT_FIELD.typeName, id?.trimUiIdPrefix(), tags.map { it.trimUiTagPrefix() }, modifiers, attributes),
    UiStatefulNode {
    var value: String = ""
        set(value) {
            val normalized = if (mode == UiTextFieldMode.SINGLE_LINE) value.replace('\n', ' ') else value
            if (!filter.accepts(normalized)) return
            field = normalized
            caret = caret.coerceIn(0, field.length)
            selectionAnchor = selectionAnchor?.coerceIn(0, field.length)
            this.attributes["value"] = field
        }

    var placeholder: String = this.attributes["placeholder"].orEmpty()
        set(value) {
            field = value
            this.attributes["placeholder"] = value
        }

    var mode: UiTextFieldMode = mode
        set(value) {
            field = value
            this.attributes["mode"] = when (value) {
                UiTextFieldMode.SINGLE_LINE -> "single-line"
                UiTextFieldMode.MULTI_LINE -> "multi-line"
            }
            this.value = this.value
        }

    var filter: UiTextInputFilter = filter
        set(value) {
            field = value
            this.attributes["filter"] = value.name.lowercase()
            this.value = this.value
        }

    var multiCaret: Boolean = multiCaret
        set(value) {
            field = value
            this.attributes["multi-caret"] = value.toString()
        }

    var caret: Int = this.value.length
        private set

    var selectionAnchor: Int? = null
        private set

    val carets: MutableList<Int> = mutableListOf(this.value.length)

    val multiline: Boolean get() = mode == UiTextFieldMode.MULTI_LINE

    val selectionStart: Int get() = minOf(caret, selectionAnchor ?: caret)

    val selectionEnd: Int get() = maxOf(caret, selectionAnchor ?: caret)

    val hasSelection: Boolean get() = selectionStart != selectionEnd

    init {
        this.mode = mode
        this.filter = filter
        this.multiCaret = multiCaret
        this.value = value
        moveCaret(this.value.length)
    }

    fun insert(text: String): Boolean {
        if (text.isEmpty()) return false
        val sanitized = if (multiline) text else text.replace('\n', ' ')
        val start = selectionStart
        val end = selectionEnd
        val next = value.replaceRange(start, end, sanitized)
        if (!filter.accepts(next)) return false
        value = next
        moveCaret(start + sanitized.length)
        return true
    }

    fun backspace(): Boolean {
        if (hasSelection) return deleteSelection()
        if (caret <= 0) return false
        val nextCaret = caret - 1
        value = value.removeRange(nextCaret, caret)
        moveCaret(nextCaret)
        return true
    }

    fun deleteForward(): Boolean {
        if (hasSelection) return deleteSelection()
        if (caret >= value.length) return false
        value = value.removeRange(caret, caret + 1)
        return true
    }

    fun moveCaret(position: Int, select: Boolean = false) {
        val previous = caret
        caret = position.coerceIn(0, value.length)
        selectionAnchor = if (select) selectionAnchor ?: previous else null
        if (carets.isEmpty()) carets += caret else carets[0] = caret
    }

    fun setSelection(anchor: Int, active: Int) {
        selectionAnchor = anchor.coerceIn(0, value.length)
        moveCaret(active.coerceIn(0, value.length), select = true)
    }

    fun selectAll() {
        selectionAnchor = 0
        moveCaret(value.length, select = true)
    }

    fun clearSelection() {
        selectionAnchor = null
    }

    override fun exportState(): UiNodePersistentState = TextFieldPersistentState(
        value = value,
        caret = caret,
        selectionAnchor = selectionAnchor,
        carets = carets.toList(),
    )

    override fun importState(state: UiNodePersistentState) {
        if (state !is TextFieldPersistentState) return
        value = state.value
        caret = state.caret.coerceIn(0, value.length)
        selectionAnchor = state.selectionAnchor?.coerceIn(0, value.length)
        carets.clear()
        carets += state.carets.map { it.coerceIn(0, value.length) }
        if (carets.isEmpty()) carets += caret
    }

    private fun deleteSelection(): Boolean {
        val start = selectionStart
        val end = selectionEnd
        if (start == end) return false
        value = value.removeRange(start, end)
        moveCaret(start)
        return true
    }
}

internal fun Map<String, String>.readSliderValue(name: String, fallback: Float): Float =
    this[name]?.toFloatOrNull() ?: fallback

internal fun Map<String, String>.readBoolean(name: String, fallback: Boolean = false): Boolean = when (this[name]?.lowercase()) {
    "true", "yes", "1", "enabled", "checked" -> true
    "false", "no", "0", "disabled", "unchecked" -> false
    else -> fallback
}

private fun String.trimUiIdPrefix() = removePrefix("#")

private fun String.trimUiTagPrefix() = removePrefix(".")
