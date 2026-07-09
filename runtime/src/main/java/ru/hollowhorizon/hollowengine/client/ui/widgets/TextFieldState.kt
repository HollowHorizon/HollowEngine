package ru.hollowhorizon.hollowengine.client.ui.widgets

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ru.hollowhorizon.hollowengine.client.ui.UiColor
import ru.hollowhorizon.hollowengine.client.ui.style.DefaultUiFontSize
import ru.hollowhorizon.hollowengine.client.ui.text.Shadow

/**
 * Compose-observable holder for an editable text field. Owns the whole edit model - text plus a
 * list of carets/selections, and exposes edit operations directly. [caretRanges] is the single
 * source of truth; the primary caret is always the last range. Also carries the field's view
 * config (font, wrap, colours) so the widget itself stays a thin `EditableTextField(state)`.
 */
class TextFieldState(
    initialText: String = "",
    initialCaret: Int = initialText.length,
    var multiline: Boolean = false,
    var readOnly: Boolean = false,
    var filter: UiTextInputFilter = UiTextInputFilter.ANY,
    var indentSize: Int? = 4,
    var autoPairs: Boolean = true,
    var multiCaret: Boolean = true,
    fontSize: Float = DefaultUiFontSize,
    fontFamily: String? = null,
    wrap: Boolean = false,
    caretColor: UiColor = DefaultTextFieldCaretColor,
    selectionColor: UiColor = DefaultTextFieldSelectionColor,
    textShadow: Shadow? = Shadow(offsetX = 1f, offsetY = 1f),
    private val historyMergeWindowNanos: Long = TextFieldStateHistoryMergeWindowNanos,
    private val nanoTime: () -> Long = System::nanoTime,
) {
    var fontSize: Float by mutableStateOf(fontSize)
    var fontFamily: String? by mutableStateOf(fontFamily)
    var wrap: Boolean by mutableStateOf(wrap)
    var caretColor: UiColor by mutableStateOf(caretColor)
    var selectionColor: UiColor by mutableStateOf(selectionColor)
    var textShadow: Shadow? by mutableStateOf(textShadow)

    var text: String by mutableStateOf(initialText.normalizeEditorLineEndings())
        private set

    var caretRanges: List<UiTextCaret> by mutableStateOf(
        listOf(UiTextCaret(initialCaret.coerceIn(0, text.length))),
    )
        private set

    /** Bumped whenever carets move or edit, so the caret blink can reset to fully-visible. */
    var caretVisibilityRevision: Int by mutableStateOf(0)
        private set

    val primaryCaret: UiTextCaret get() = caretRanges.last()
    val caret: Int get() = primaryCaret.position
    val selectionAnchor: Int? get() = primaryCaret.selectionAnchor
    val hasSelection: Boolean get() = caretRanges.any { it.hasSelection }

    private val undoStack = ArrayDeque<TextFieldHistoryEntry>()
    private val redoStack = ArrayDeque<TextFieldHistoryEntry>()
    private var lastHistoryEditNanos = Long.MIN_VALUE
    private var restoringHistory = false

    fun insert(insertion: String): Boolean {
        if (readOnly || insertion.isEmpty()) return false
        val normalized = insertion.normalizeEditorLineEndings()
        val sanitized = if (multiline) normalized else normalized.replace('\n', ' ')
        return replaceSelectedRanges(sanitized)
    }

    fun typeCharacter(char: Char): Boolean {
        if (!autoPairs) return insert(char.toString())
        if (trySkipClosingPair(char)) return true

        val closing = AutoPairClosings[char] ?: return insert(char.toString())
        if (activeCaretRanges().any { it.hasSelection }) return insert(char.toString())

        val changed = insert("$char$closing")
        if (changed) moveCarets({ range -> range.position - 1 })
        return changed
    }

    fun backspace(word: Boolean = false): Boolean {
        if (readOnly) return false
        if (activeCaretRanges().any { it.hasSelection }) return replaceSelectedRanges("")
        if (!word && multiline && deleteWhitespaceOnlyLineBeforeCaret()) return true
        if (!word && autoPairs) {
            val pairRanges = activeCaretRanges().mapNotNull { range ->
                val opening = text.getOrNull(range.position - 1) ?: return@mapNotNull null
                val closing = text.getOrNull(range.position) ?: return@mapNotNull null
                if (AutoPairClosings[opening] == closing) TextEditRange(range.position - 1, range.position + 1) else null
            }
            if (pairRanges.size == activeCaretRanges().size && pairRanges.isNotEmpty()) {
                return replaceRanges(pairRanges, "")
            }
        }
        val ranges = activeCaretRanges().mapNotNull { range ->
            if (range.position <= 0) null
            else TextEditRange(if (word) wordLeft(text, range.position) else range.position - 1, range.position)
        }
        return replaceRanges(ranges, "")
    }

    fun deleteForward(word: Boolean = false): Boolean {
        if (readOnly) return false
        if (activeCaretRanges().any { it.hasSelection }) return replaceSelectedRanges("")
        val ranges = activeCaretRanges().mapNotNull { range ->
            if (range.position >= text.length) null
            else TextEditRange(range.position, if (word) wordRight(text, range.position) else range.position + 1)
        }
        return replaceRanges(ranges, "")
    }

    fun indent(): Boolean {
        if (readOnly) return false
        val size = indentSize ?: return false
        if (!multiline) return false
        if (activeCaretRanges().all { !it.hasSelection }) return insert(" ".repeat(size))

        val lineStarts = selectedLineStarts()
        if (lineStarts.isEmpty()) return false
        return replaceRanges(lineStarts.map { TextEditRange(it, it) }, " ".repeat(size))
    }

    fun unindent(): Boolean {
        if (readOnly) return false
        val size = indentSize ?: return false
        if (!multiline) return false
        val ranges = selectedLineStarts().mapNotNull { start ->
            val end = (start + size).coerceAtMost(text.length)
            val removable = text.substring(start, end).takeWhile { it == ' ' }.length
            removable.takeIf { it > 0 }?.let { TextEditRange(start, start + it) }
        }
        return replaceRanges(ranges, "")
    }

    fun insertNewlineWithIndent(): Boolean {
        if (readOnly || !multiline) return false
        val size = indentSize ?: return insert("\n")
        if (activeCaretRanges().any { it.hasSelection }) return insert("\n")

        val position = caret.coerceIn(0, text.length)
        val lineStart = if (position == 0) 0 else {
            val lastLineBreak = text.lastIndexOf('\n', position - 1)
            if (lastLineBreak < 0) 0 else lastLineBreak + 1
        }
        val lineEnd = text.indexOf('\n', position).let { if (it < 0) text.length else it }
        val line = text.substring(lineStart, lineEnd)
        val column = position - lineStart
        val context = newlineIndentContext(lineStart, line, column, size)
        val baseIndent = context.baseIndent
        val beforeCaret = line.substring(0, column).trimEnd()
        val afterCaret = line.substring(column).trimStart()
        val opensBlock = beforeCaret.endsWith("{") || beforeCaret.endsWith("(") || beforeCaret.endsWith("[") ||
                line.isBlank() && context.previousOpensBlock
        val closesBlock = afterCaret.startsWith("}") || afterCaret.startsWith(")") || afterCaret.startsWith("]")

        return if (opensBlock && closesBlock) {
            val innerIndent = baseIndent + " ".repeat(size)
            val changed = insert("\n$innerIndent\n$baseIndent")
            if (changed) moveCarets({ range -> range.position - baseIndent.length - 1 })
            changed
        } else {
            val nextIndent = if (opensBlock) baseIndent + " ".repeat(size) else baseIndent
            insert("\n$nextIndent")
        }
    }

    /** Replaces the whole text (e.g. external/programmatic change); resets undo history. */
    fun setText(next: String, moveCaretToEnd: Boolean = true) {
        val normalized = next.normalizeEditorLineEndings()
        if (!filter.accepts(normalized)) return
        val changed = normalized != text
        text = normalized
        if (changed) {
            undoStack.clear()
            redoStack.clear()
            breakHistoryGroup()
        }
        if (moveCaretToEnd) setCarets(listOf(UiTextCaret(text.length)))
        else setCarets(caretRanges.map { it.coerceIn(text.length) })
    }

    fun moveCaret(position: Int, select: Boolean = false) {
        val previous = primaryCaret
        val anchor = if (select) previous.selectionAnchor ?: previous.position else null
        setCarets(listOf(UiTextCaret(position.coerceIn(0, text.length), anchor)))
        if (caretRanges.last() != previous) breakHistoryGroup()
    }

    fun moveCarets(transform: (UiTextCaret) -> Int, select: Boolean = false) {
        val next = activeCaretRanges().map { range ->
            val position = transform(range).coerceIn(0, text.length)
            UiTextCaret(position, if (select) range.selectionAnchor ?: range.position else null)
        }
        setCarets(next)
    }

    fun addCaret(position: Int) {
        if (!multiCaret) return moveCaret(position)
        val caret = UiTextCaret(position.coerceIn(0, text.length))
        val ranges = activeCaretRanges()
        val next = if (ranges.any { !it.hasSelection && it.position == caret.position }) {
            ranges.filterNot { !it.hasSelection && it.position == caret.position }
        } else {
            ranges + caret
        }
        setCarets(next.ifEmpty { listOf(caret) })
    }

    fun addCaretRange(range: UiTextCaret) {
        if (!multiCaret) return setCarets(listOf(range))
        val normalized = range.coerceIn(text.length)
        val next = activeCaretRanges().filterNot { existing ->
            (!existing.hasSelection && existing.position in normalized.selectionStart..normalized.selectionEnd) ||
                    (existing.selectionStart == normalized.selectionStart && existing.selectionEnd == normalized.selectionEnd)
        } + normalized
        setCarets(next.ifEmpty { listOf(normalized) })
    }

    fun setSelection(anchor: Int, active: Int) {
        val a = anchor.coerceIn(0, text.length)
        setCarets(listOf(UiTextCaret(active.coerceIn(0, text.length), a)))
    }

    fun selectAll() = setCarets(listOf(UiTextCaret(text.length, 0)))

    fun clearSelection() = setCarets(activeCaretRanges().map { UiTextCaret(it.position) })

    fun selectedText(): String? {
        val selections = activeCaretRanges().filter { it.hasSelection }
        if (selections.isEmpty()) return null
        return selections.sortedBy { it.selectionStart }.joinToString("\n") { text.substring(it.selectionStart, it.selectionEnd) }
    }

    fun setCarets(ranges: List<UiTextCaret>) {
        val previous = caretRanges
        applyCaretRanges(ranges)
        if (caretRanges != previous) caretVisibilityRevision++
    }

    fun undo(): Boolean {
        if (readOnly) return false
        val entry = undoStack.removeLastOrNull() ?: return false
        redoStack.addLast(historyEntry())
        restoreHistory(entry)
        breakHistoryGroup()
        return true
    }

    fun redo(): Boolean {
        if (readOnly) return false
        val entry = redoStack.removeLastOrNull() ?: return false
        undoStack.addLast(historyEntry())
        restoreHistory(entry)
        breakHistoryGroup()
        return true
    }

    private fun replaceSelectedRanges(replacement: String): Boolean {
        val ranges = activeCaretRanges().map { TextEditRange(it.selectionStart, it.selectionEnd) }
        return replaceRanges(ranges, replacement)
    }

    private fun replaceRanges(ranges: List<TextEditRange>, replacement: String): Boolean {
        if (readOnly) return false
        val edits = ranges
            .map { TextEditRange(it.start.coerceIn(0, text.length), it.end.coerceIn(0, text.length)) }
            .filter { it.start != it.end || replacement.isNotEmpty() }
            .sortedWith(compareBy<TextEditRange> { it.start }.thenBy { it.end })
            .fold(mutableListOf<TextEditRange>()) { acc, range ->
                if (acc.isEmpty() || range.start >= acc.last().end) acc += range
                else if (range.end > acc.last().end) acc[acc.lastIndex] = TextEditRange(acc.last().start, range.end)
                acc
            }
        if (edits.isEmpty()) return false

        val nextValue = buildString {
            var cursor = 0
            for (edit in edits) {
                append(text, cursor, edit.start)
                append(replacement)
                cursor = edit.end
            }
            append(text, cursor, text.length)
        }
        if (!filter.accepts(nextValue)) return false
        recordHistorySnapshot()

        var offset = 0
        val nextCarets = edits.map { edit ->
            val position = edit.start + offset + replacement.length
            offset += replacement.length - (edit.end - edit.start)
            UiTextCaret(position)
        }
        text = nextValue
        setCarets(nextCarets)
        return true
    }

    private fun applyCaretRanges(ranges: List<UiTextCaret>) {
        caretRanges = ranges
            .ifEmpty { listOf(UiTextCaret(0)) }
            .map { it.coerceIn(text.length) }
            .let { if (multiCaret) it else listOf(it.last()) }
            .distinctBy { it.position to it.selectionAnchor }
    }

    private fun activeCaretRanges(): List<UiTextCaret> = if (multiCaret) caretRanges else caretRanges.take(1)

    private fun trySkipClosingPair(char: Char): Boolean {
        if (char !in AutoPairClosings.values) return false
        if (activeCaretRanges().any { it.hasSelection || text.getOrNull(it.position) != char }) return false
        moveCarets({ range -> range.position + 1 })
        return true
    }

    private fun deleteWhitespaceOnlyLineBeforeCaret(): Boolean {
        val position = caret.coerceIn(0, text.length)
        val lineStart = text.lastIndexOf('\n', (position - 1).coerceAtLeast(0)).let { if (it < 0) 0 else it + 1 }
        val lineEnd = text.indexOf('\n', position).let { if (it < 0) text.length else it }
        if (lineEnd <= lineStart) return false
        if (text.substring(lineStart, lineEnd).any { it != ' ' }) return false

        val deleteStart = if (lineStart > 0) lineStart - 1 else 0
        val deleteEnd = if (lineStart == 0 && lineEnd < text.length) lineEnd + 1 else lineEnd
        return replaceRanges(listOf(TextEditRange(deleteStart, deleteEnd)), "")
    }

    private fun selectedLineStarts(): List<Int> {
        val starts = linkedSetOf<Int>()
        for (range in activeCaretRanges()) {
            val from = range.selectionStart.coerceIn(0, text.length)
            val rawTo = range.selectionEnd.coerceIn(0, text.length)
            val to = if (rawTo > from && text.getOrNull(rawTo - 1) == '\n') rawTo - 1 else rawTo
            var lineStart = text.lastIndexOf('\n', (from - 1).coerceAtLeast(0)).let { if (it < 0) 0 else it + 1 }
            while (lineStart <= to) {
                starts += lineStart
                val next = text.indexOf('\n', lineStart)
                if (next < 0 || next >= to) break
                lineStart = next + 1
            }
        }
        return starts.toList()
    }

    private fun newlineIndentContext(lineStart: Int, line: String, column: Int, indentSize: Int): NewlineIndentContext {
        if (line.isNotBlank()) return NewlineIndentContext(line.takeWhile { it == ' ' }, previousOpensBlock = false)

        var scanEnd = (lineStart - 1).coerceAtLeast(0)
        while (scanEnd > 0) {
            val previousStart = text.lastIndexOf('\n', scanEnd - 1).let { if (it < 0) 0 else it + 1 }
            val previousLine = text.substring(previousStart, scanEnd)
            if (previousLine.isNotBlank()) {
                val indent = previousLine.takeWhile { it == ' ' }
                val opens = previousLine.trimEnd().let { it.endsWith("{") || it.endsWith("(") || it.endsWith("[") }
                return NewlineIndentContext(indent, opens)
            }
            scanEnd = (previousStart - 1).coerceAtLeast(0)
            if (previousStart == 0) break
        }
        return NewlineIndentContext(" ".repeat((column / indentSize) * indentSize), previousOpensBlock = false)
    }

    private fun recordHistorySnapshot() {
        if (restoringHistory) return
        val now = nanoTime()
        val entry = historyEntry()
        val startsNewGroup = lastHistoryEditNanos == Long.MIN_VALUE || now - lastHistoryEditNanos > historyMergeWindowNanos
        if (startsNewGroup && undoStack.lastOrNull() != entry) {
            undoStack.addLast(entry)
            while (undoStack.size > TextFieldStateHistoryLimit) undoStack.removeFirst()
        }
        lastHistoryEditNanos = now
        redoStack.clear()
    }

    private fun breakHistoryGroup() {
        lastHistoryEditNanos = Long.MIN_VALUE
    }

    private fun historyEntry(): TextFieldHistoryEntry = TextFieldHistoryEntry(text, caretRanges.toList())

    private fun restoreHistory(entry: TextFieldHistoryEntry) {
        restoringHistory = true
        try {
            text = entry.text
            applyCaretRanges(entry.caretRanges.map { it.coerceIn(text.length) })
            caretVisibilityRevision++
        } finally {
            restoringHistory = false
        }
    }

    private data class TextEditRange(val start: Int, val end: Int)
    private data class NewlineIndentContext(val baseIndent: String, val previousOpensBlock: Boolean)
    private data class TextFieldHistoryEntry(val text: String, val caretRanges: List<UiTextCaret>)
}

private val DefaultTextFieldCaretColor = UiColor(0.85f, 0.88f, 0.95f, 1f)
private val DefaultTextFieldSelectionColor = UiColor(0.28f, 0.54f, 0.95f, 0.35f)

private const val TextFieldStateHistoryLimit = 128
private const val TextFieldStateHistoryMergeWindowNanos = 600_000_000L
private val AutoPairClosings = mapOf(
    '(' to ')',
    '[' to ']',
    '{' to '}',
    '"' to '"',
    '\'' to '\'',
)
