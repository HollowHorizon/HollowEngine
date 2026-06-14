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
            if (field == value) return
            field = value
            this.attributes["min"] = value.toString()
            this.value = this.value
            invalidateLayout()
        }

    var max: Float = max
        set(value) {
            if (field == value) return
            field = value
            this.attributes["max"] = value.toString()
            this.value = this.value
            invalidateLayout()
        }

    var step: Float = step.coerceAtLeast(0f)
        set(value) {
            val normalized = value.coerceAtLeast(0f)
            if (field == normalized) return
            field = normalized
            this.attributes["step"] = field.toString()
            this.value = this.value
            invalidateLayout()
        }

    var value: Float = 0f
        set(value) {
            val normalized = normalize(value)
            if (field == normalized) {
                this.attributes["value"] = field.toString()
                return
            }
            field = normalized
            this.attributes["value"] = field.toString()
            invalidateLayout()
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
            if (field == value) return
            field = value
            this.attributes["checked"] = value.toString()
            if (value) states += UiState.SELECTED else states -= UiState.SELECTED
            invalidateLayout()
        }

    var variant: UiCheckboxVariant = variant
        set(value) {
            if (field == value) return
            field = value
            this.attributes["variant"] = value.name.lowercase()
            invalidateLayout()
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
    syntaxHighlighter: UiSyntaxHighlighter? = null,
    completionContributor: UiCompletionContributor? = null,
    diagnostics: List<UiTextDiagnostic> = emptyList(),
    inlayHints: List<UiInlayHint> = emptyList(),
    inlayHintsProvider: UiInlayHintsProvider? = null,
    onChange: ((String) -> Unit)? = null,
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
            if (field == normalized) {
                this.attributes["value"] = field
                return
            }
            field = normalized
            caret = caret.coerceIn(0, field.length)
            selectionAnchor = selectionAnchor?.coerceIn(0, field.length)
            caretRanges.replaceAll { it.coerceIn(field.length) }
            completionAnchor = completionAnchor.coerceIn(0, field.length)
            completionItems = emptyList()
            this.attributes["value"] = field
            invalidateLayout()
            onChange?.invoke(field)
        }

    var placeholder: String = this.attributes["placeholder"].orEmpty()
        set(value) {
            if (field == value) return
            field = value
            this.attributes["placeholder"] = value
            invalidateLayout()
        }

    var mode: UiTextFieldMode = mode
        set(value) {
            if (field == value) return
            field = value
            this.attributes["mode"] = when (value) {
                UiTextFieldMode.SINGLE_LINE -> "single-line"
                UiTextFieldMode.MULTI_LINE -> "multi-line"
            }
            this.value = this.value
            invalidateLayout()
        }

    var filter: UiTextInputFilter = filter
        set(value) {
            if (field == value) return
            field = value
            this.attributes["filter"] = value.name.lowercase()
            this.value = this.value
            invalidateLayout()
        }

    var multiCaret: Boolean = multiCaret
        set(value) {
            if (field == value) return
            field = value
            this.attributes["multi-caret"] = value.toString()
            if (!value && caretRanges.size > 1) {
                setCaretRanges(listOf(caretRanges.last()))
            }
            invalidateLayout()
        }

    var syntaxHighlighter: UiSyntaxHighlighter? = syntaxHighlighter
        set(value) {
            if (field === value) return
            field = value
            invalidateLayout()
        }
    var completionContributor: UiCompletionContributor? = completionContributor
        set(value) {
            if (field === value) return
            field = value
            invalidateLayout()
        }
    var diagnostics: List<UiTextDiagnostic> = diagnostics
        set(value) {
            if (field == value) return
            field = value
            invalidateLayout()
        }
    var inlayHints: List<UiInlayHint> = inlayHints
        set(value) {
            if (field == value) return
            field = value
            clearInlayHintCache()
            invalidateLayout()
        }
    var inlayHintsProvider: UiInlayHintsProvider? = inlayHintsProvider
        set(value) {
            if (field === value) return
            field = value
            clearInlayHintCache()
            invalidateLayout()
        }
    var onChange: ((String) -> Unit)? = onChange
    var completionItems: List<UiTextCompletion> = emptyList()
        private set
    var completionAnchor: Int = this.value.length
        private set
    var completionSelectedIndex: Int = 0
        private set
    private var completionReplacementStart: Int = this.value.length
    private var completionReplacementEnd: Int = this.value.length
    private var completionLineStart: Int = 0
    private var completionLineEnd: Int = 0
    private var cachedInlayProvider: UiInlayHintsProvider? = null
    private var cachedInlayText: String? = null
    private var cachedInlayHints: List<UiInlayHint> = emptyList()

    var caret: Int = this.value.length
        private set

    var selectionAnchor: Int? = null
        private set

    val carets: MutableList<Int> = mutableListOf(this.value.length)

    val caretRanges: MutableList<UiTextCaret> = mutableListOf(UiTextCaret(this.value.length))

    var caretVisibilityRevision: Long = 0L
        private set

    val multiline: Boolean get() = mode == UiTextFieldMode.MULTI_LINE

    val selectionStart: Int get() = minOf(caret, selectionAnchor ?: caret)

    val selectionEnd: Int get() = maxOf(caret, selectionAnchor ?: caret)

    val hasSelection: Boolean get() = selectionStart != selectionEnd

    init {
        this.mode = mode
        this.filter = filter
        this.multiCaret = multiCaret
        this.syntaxHighlighter = syntaxHighlighter
        this.completionContributor = completionContributor
        this.diagnostics = diagnostics
        this.inlayHints = inlayHints
        this.inlayHintsProvider = inlayHintsProvider
        this.value = value
        moveCaret(this.value.length)
    }

    fun insert(text: String): Boolean {
        if (text.isEmpty()) return false
        val sanitized = if (multiline) text else text.replace('\n', ' ')
        return replaceSelectedRanges(sanitized)
    }

    fun backspace(word: Boolean = false): Boolean {
        if (activeCaretRanges().any { it.hasSelection }) return replaceSelectedRanges("")
        val ranges = activeCaretRanges().mapNotNull { range ->
            if (range.position <= 0) {
                null
            } else {
                val start = if (word) editorWordLeft(value, range.position) else range.position - 1
                TextEditRange(start, range.position)
            }
        }
        return replaceRanges(ranges, "")
    }

    fun deleteForward(word: Boolean = false): Boolean {
        if (activeCaretRanges().any { it.hasSelection }) return replaceSelectedRanges("")
        val ranges = activeCaretRanges().mapNotNull { range ->
            if (range.position >= value.length) {
                null
            } else {
                val end = if (word) editorWordRight(value, range.position) else range.position + 1
                TextEditRange(range.position, end)
            }
        }
        return replaceRanges(ranges, "")
    }

    fun moveCaret(position: Int, select: Boolean = false) {
        val previous = caret
        val previousAnchor = selectionAnchor
        caret = position.coerceIn(0, value.length)
        selectionAnchor = if (select) selectionAnchor ?: previous else null
        setCaretRangesInternal(listOf(UiTextCaret(caret, selectionAnchor)), updatePrimary = false)
        closeCompletionsIfCaretLeftLine()
        if (caret != previous || selectionAnchor != previousAnchor) caretVisibilityRevision++
    }

    fun moveCarets(transform: (UiTextCaret) -> Int, select: Boolean = false) {
        val previous = caretRanges.toList()
        val next = activeCaretRanges().map { range ->
            val position = transform(range).coerceIn(0, value.length)
            UiTextCaret(position, if (select) range.selectionAnchor ?: range.position else null)
        }
        setCaretRanges(next)
        closeCompletionsIfCaretLeftLine()
        if (previous != caretRanges) caretVisibilityRevision++
    }

    fun addCaret(position: Int) {
        if (!multiCaret) {
            moveCaret(position)
            return
        }
        val caret = UiTextCaret(position.coerceIn(0, value.length))
        val ranges = activeCaretRanges()
        val next = if (ranges.any { !it.hasSelection && it.position == caret.position }) {
            ranges.filterNot { !it.hasSelection && it.position == caret.position }
        } else {
            ranges + caret
        }
        setCaretRanges(next.ifEmpty { listOf(caret) })
    }

    fun addCaretRange(range: UiTextCaret) {
        if (!multiCaret) {
            setCaretRanges(listOf(range))
            return
        }
        val normalized = range.coerceIn(value.length)
        val ranges = activeCaretRanges()
        val next = ranges
            .filterNot { existing ->
                (!existing.hasSelection && existing.position in normalized.selectionStart..normalized.selectionEnd) ||
                        (existing.selectionStart == normalized.selectionStart && existing.selectionEnd == normalized.selectionEnd)
            } + normalized
        setCaretRanges(next.ifEmpty { listOf(normalized) })
    }

    fun removeCaretRangeAt(position: Int): Boolean {
        val index = activeCaretRanges().indexOfFirst { range ->
            if (range.hasSelection) position in range.selectionStart..range.selectionEnd else range.position == position
        }
        if (index < 0) return false
        val next = activeCaretRanges().toMutableList().also { it.removeAt(index) }
        setCaretRanges(next.ifEmpty { listOf(UiTextCaret(position.coerceIn(0, value.length))) })
        return true
    }

    fun updateLastCaretRange(anchor: Int, active: Int) {
        val ranges = activeCaretRanges().toMutableList()
        val range = UiTextCaret(active.coerceIn(0, value.length), anchor.coerceIn(0, value.length))
        if (ranges.isEmpty()) ranges += range else ranges[ranges.lastIndex] = range
        setCaretRanges(ranges)
    }

    fun setSelection(anchor: Int, active: Int) {
        selectionAnchor = anchor.coerceIn(0, value.length)
        moveCaret(active.coerceIn(0, value.length), select = true)
    }

    fun setCaretRanges(ranges: List<UiTextCaret>) {
        val previousCaret = caret
        val previousAnchor = selectionAnchor
        val previousRanges = caretRanges.toList()
        setCaretRangesInternal(ranges)
        if (caret != previousCaret || selectionAnchor != previousAnchor || caretRanges != previousRanges) {
            caretVisibilityRevision++
        }
    }

    fun selectAll() {
        selectionAnchor = 0
        moveCaret(value.length, select = true)
    }

    fun selectedText(): String? {
        val selections = activeCaretRanges().filter { it.hasSelection }
        if (selections.isEmpty()) return null
        return selections
            .sortedBy { it.selectionStart }
            .joinToString("\n") { value.substring(it.selectionStart, it.selectionEnd) }
    }

    fun clearSelection() {
        selectionAnchor = null
        setCaretRangesInternal(listOf(UiTextCaret(caret)))
    }

    fun openCompletions(): Boolean {
        val contributor = completionContributor ?: return false
        val items = contributor.complete(UiCompletionContext(value, caret))
            .filter { it.label.isNotBlank() || it.insertText.isNotBlank() }
        val previousItems = completionItems
        val previousAnchor = completionAnchor
        val replacement = completionReplacementRange(value, caret)
        completionItems = items
        completionAnchor = caret
        completionSelectedIndex = 0
        completionReplacementStart = replacement.first
        completionReplacementEnd = replacement.last
        val line = completionLineRange(value, caret)
        completionLineStart = line.first
        completionLineEnd = line.last
        return previousItems != completionItems || previousAnchor != completionAnchor
    }

    fun closeCompletions(): Boolean {
        if (completionItems.isEmpty()) return false
        completionItems = emptyList()
        completionSelectedIndex = 0
        return true
    }

    fun moveCompletionSelection(delta: Int): Boolean {
        if (completionItems.isEmpty()) return false
        val previous = completionSelectedIndex
        completionSelectedIndex = (completionSelectedIndex + delta).floorMod(completionItems.size)
        return previous != completionSelectedIndex
    }

    fun acceptCompletion(index: Int = 0): Boolean {
        val item = completionItems.getOrNull(index) ?: return false
        val insertText = item.insertText.ifEmpty { item.label }
        val changed = replaceCompletionRange(insertText, item.caretOffset)
        completionItems = emptyList()
        return changed
    }

    fun currentInlayHints(): List<UiInlayHint> {
        val provider = inlayHintsProvider ?: return inlayHints
        if (cachedInlayProvider === provider && cachedInlayText == value) return cachedInlayHints
        val hints = provider.hints(value)
        cachedInlayProvider = provider
        cachedInlayText = value
        cachedInlayHints = hints
        return hints
    }

    override fun exportState(): UiNodePersistentState = TextFieldPersistentState(
        value = value,
        caret = caret,
        selectionAnchor = selectionAnchor,
        carets = carets.toList(),
        caretRanges = caretRanges.toList(),
        caretVisibilityRevision = caretVisibilityRevision,
    )

    override fun importState(state: UiNodePersistentState) {
        if (state !is TextFieldPersistentState) return
        value = state.value
        caret = state.caret.coerceIn(0, value.length)
        selectionAnchor = state.selectionAnchor?.coerceIn(0, value.length)
        val ranges = state.caretRanges.takeIf { it.isNotEmpty() }
            ?: state.carets.map { UiTextCaret(it) }.takeIf { it.isNotEmpty() }
            ?: listOf(UiTextCaret(caret, selectionAnchor))
        setCaretRangesInternal(ranges.map { it.coerceIn(value.length) })
        caretVisibilityRevision = state.caretVisibilityRevision
    }

    private fun replaceSelectedRanges(replacement: String): Boolean {
        val ranges = activeCaretRanges().map { TextEditRange(it.selectionStart, it.selectionEnd) }
        return replaceRanges(ranges, replacement)
    }

    private fun clearInlayHintCache() {
        cachedInlayProvider = null
        cachedInlayText = null
        cachedInlayHints = emptyList()
    }

    private fun replaceRanges(ranges: List<TextEditRange>, replacement: String): Boolean {
        val edits = ranges
            .map { TextEditRange(it.start.coerceIn(0, value.length), it.end.coerceIn(0, value.length)) }
            .filter { it.start != it.end || replacement.isNotEmpty() }
            .sortedWith(compareBy<TextEditRange> { it.start }.thenBy { it.end })
            .fold(mutableListOf<TextEditRange>()) { acc, range ->
                if (acc.isEmpty() || range.start >= acc.last().end) {
                    acc += range
                } else if (range.end > acc.last().end) {
                    acc[acc.lastIndex] = TextEditRange(acc.last().start, range.end)
                }
                acc
            }
        if (edits.isEmpty()) return false

        val nextValue = buildString {
            var cursor = 0
            for (edit in edits) {
                append(value, cursor, edit.start)
                append(replacement)
                cursor = edit.end
            }
            append(value, cursor, value.length)
        }
        if (!filter.accepts(nextValue)) return false

        var offset = 0
        val nextCarets = edits.map { edit ->
            val position = edit.start + offset + replacement.length
            offset += replacement.length - (edit.end - edit.start)
            UiTextCaret(position)
        }
        value = nextValue
        setCaretRanges(nextCarets)
        completionItems = emptyList()
        return true
    }

    private fun replaceCompletionRange(replacement: String, caretOffset: Int?): Boolean {
        val start = completionReplacementStart.coerceIn(0, value.length)
        val end = completionReplacementEnd.coerceIn(start, value.length)
        val nextValue = value.substring(0, start) + replacement + value.substring(end)
        if (!filter.accepts(nextValue)) return false
        value = nextValue
        val nextCaret = start + (caretOffset ?: replacement.length).coerceIn(0, replacement.length)
        setCaretRanges(listOf(UiTextCaret(nextCaret)))
        return true
    }

    private fun closeCompletionsIfCaretLeftLine() {
        if (completionItems.isEmpty()) return
        if (caret < completionLineStart || caret > completionLineEnd) closeCompletions()
    }

    private fun activeCaretRanges(): List<UiTextCaret> {
        val ranges = if (multiCaret) caretRanges else caretRanges.take(1)
        return ranges.ifEmpty { listOf(UiTextCaret(caret, selectionAnchor)) }
    }

    private fun setCaretRangesInternal(ranges: List<UiTextCaret>, updatePrimary: Boolean = true) {
        val normalized = ranges
            .ifEmpty { listOf(UiTextCaret(0)) }
            .map { it.coerceIn(value.length) }
            .let { if (multiCaret) it else listOf(it.last()) }
            .distinctBy { it.position to it.selectionAnchor }
        caretRanges.clear()
        caretRanges += normalized
        carets.clear()
        carets += caretRanges.map { it.position }
        if (carets.isEmpty()) carets += 0
        if (updatePrimary) {
            val primary = caretRanges.last()
            caret = primary.position
            selectionAnchor = primary.selectionAnchor
        }
    }

    private data class TextEditRange(val start: Int, val end: Int)
}

private fun editorWordLeft(text: String, position: Int): Int {
    var index = position.coerceIn(0, text.length)
    while (index > 0 && text[index - 1].isWhitespace()) index--
    while (index > 0 && text[index - 1].isEditorWordChar()) index--
    if (index == position.coerceIn(0, text.length) && index > 0) index--
    return index
}

private fun editorWordRight(text: String, position: Int): Int {
    var index = position.coerceIn(0, text.length)
    while (index < text.length && text[index].isWhitespace()) index++
    while (index < text.length && text[index].isEditorWordChar()) index++
    if (index == position.coerceIn(0, text.length) && index < text.length) index++
    return index
}

private fun Char.isEditorWordChar(): Boolean = this == '_' || isLetterOrDigit()

private fun completionReplacementRange(text: String, caret: Int): IntRange {
    val end = caret.coerceIn(0, text.length)
    var start = end
    while (start > 0 && text[start - 1].isEditorWordChar()) start--
    return start..end
}

private fun completionLineRange(text: String, caret: Int): IntRange {
    val index = caret.coerceIn(0, text.length)
    val start = text.lastIndexOf('\n', (index - 1).coerceAtLeast(0)).let { if (it < 0) 0 else it + 1 }
    val end = text.indexOf('\n', index).let { if (it < 0) text.length else it }
    return start..end
}

private fun Int.floorMod(modulo: Int): Int {
    if (modulo <= 0) return 0
    val result = this % modulo
    return if (result < 0) result + modulo else result
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
