package ru.hollowhorizon.hollowengine.client.gui.scripting.files.text

import de.fabmax.kool.math.MutableVec2i
import de.fabmax.kool.math.Vec2i
import de.fabmax.kool.math.clamp
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.MsdfFont

class ScriptTextEditorHandler(val text: MutableList<TextLine> = mutableStateListOf()) : TextEditorHandler {
    private val undoStack = ArrayDeque<UndoableAction>()
    private val redoStack = ArrayDeque<UndoableAction>()

    private data class UndoableAction(
        val startLine: Int,
        val caretLine: Int,
        val startChar: Int,
        val caretChar: Int,

        val numOldLines: Int,
        val oldLines: List<TextLine>,
        val newLines: List<TextLine>,
    )


    fun undo(onSelectionChanged: ((Int, Int, Int, Int) -> Unit)?) {
        if (undoStack.isEmpty()) return
        val action = undoStack.removeLast()
        performUndo(action, onSelectionChanged)
        redoStack.addLast(action)
    }

    fun redo(onSelectionChanged: ((Int, Int, Int, Int) -> Unit)?) {
        if (redoStack.isEmpty()) return
        val action = redoStack.removeLast()
        performRedo(action, onSelectionChanged)
        undoStack.addLast(action)
    }

    private fun performUndo(action: UndoableAction, onSelectionChanged: ((Int, Int, Int, Int) -> Unit)?) {
        val start = action.startLine
        // Remove new lines
        if (text.size >= start + action.newLines.size) {
            repeat(action.newLines.size) {
                if (text.size > start) text.removeAt(start)
            }
        } else {
            val numToRemove = text.size - start
            repeat(numToRemove) { if (text.size > start) text.removeAt(start) }
        }
        // Insert old lines
        text.addAll(start, action.oldLines)
        onSelectionChanged?.let { it(action.startLine, action.caretLine, action.startChar, action.caretChar) }
    }

    private fun performRedo(action: UndoableAction, onSelectionChanged: ((Int, Int, Int, Int) -> Unit)?) {
        val start = action.startLine
        // Remove old lines
        if (text.size >= start + action.numOldLines) {
            repeat(action.numOldLines) {
                if (text.size > start) text.removeAt(start)
            }
        } else {
            val numToRemove = text.size - start
            repeat(numToRemove) { if (text.size > start) text.removeAt(start) }
        }
        // Insert new lines
        text.addAll(start, action.newLines)
        onSelectionChanged?.let { it(action.startLine, action.caretLine, action.startChar, action.caretChar) }
    }

    override fun insertText(line: Int, caret: Int, insertion: String, textAreaScope: TextAreaScope): Vec2i {
        return replaceText(line, line, caret, caret, insertion, textAreaScope)
    }

    fun replaceAll(text: String, textAreaScope: TextAreaScope) {
        replaceText(0, this.text.lastIndex, 0, this.text.last().text.length.coerceAtLeast(0), text, textAreaScope)
    }

    override fun replaceText(
        selectionStartLine: Int,
        selectionEndLine: Int,
        selectionStartChar: Int,
        selectionEndChar: Int,
        input: String,
        textAreaScope: TextAreaScope,
    ): Vec2i {
        var replacement = input
        val startLine = this[selectionStartLine] ?: return Vec2i(selectionEndChar, selectionEndLine)
        val endLine = this[selectionEndLine] ?: return Vec2i(selectionEndChar, selectionEndLine)
        val before = startLine.before(selectionStartChar)
        val after = endLine.after(selectionEndChar)

        val escapeQuotes = before.text.lastOrNull() == '"' && after.text.firstOrNull() == '"'

        val caretPos = MutableVec2i()
        val attr = before.lastAttribs() ?: after.firstAttribs() ?: TextAttributes(MsdfFont.DEFAULT_FONT, Color.GRAY)
        if (escapeQuotes) replacement = replacement.replace("\\", "\\\\").replace("\"", "\\\"")
        val replaceLines = replacement.toLines(attr)

        caretPos.y = selectionStartLine + replaceLines.lastIndex
        val insertion = if (replaceLines.size == 1) {
            caretPos.x = before.length + replaceLines[0].length
            listOf(before + replaceLines[0] + after)
        } else {
            caretPos.x = replaceLines.last().length
            listOf(before + replaceLines[0]) + replaceLines.subList(
                1,
                replaceLines.lastIndex
            ) + (replaceLines.last() + after)
        }

        insertLines(insertion, selectionStartLine, selectionEndLine, selectionStartChar, selectionEndChar)
        return caretPos
    }

    private fun insertLines(
        insertLines: List<TextLine>,
        insertFrom: Int,
        insertTo: Int,
        selectionStartChar: Int,
        selectionEndChar: Int,
    ) {
        val oldLines = if (text.isNotEmpty() && insertFrom <= text.lastIndex) {
            val to = insertTo.coerceAtMost(text.lastIndex)
            text.subList(insertFrom, to + 1).toList()
        } else {
            emptyList()
        }

        val linesBefore = mutableListOf<TextLine>()
        val linesAfter = mutableListOf<TextLine>()
        if (insertFrom > 0) {
            linesBefore += text.subList(0, insertFrom)
        }
        if (insertTo < text.lastIndex) {
            linesAfter += text.subList(insertTo + 1, text.size)
        }

        text.clear()
        text += linesBefore
        text += insertLines
        text += linesAfter

        undoStack.addLast(
            UndoableAction(
                insertFrom,
                insertTo,
                selectionStartChar,
                selectionEndChar,
                oldLines.size,
                oldLines,
                insertLines
            )
        )
        redoStack.clear()
    }

    private fun String.toLines(attributes: TextAttributes): List<TextLine> {
        return lines().map { str -> TextLine(listOf(str to attributes)) }
    }

    operator fun get(line: Int): TextLine? {
        return if (text.isEmpty()) {
            null
        } else {
            text[line.clamp(0, text.lastIndex)]
        }
    }

    operator fun TextLine.plus(other: TextLine): TextLine {
        return TextLine(sanitize(spans + other.spans))
    }

    private fun TextLine.firstAttribs(): TextAttributes? {
        return if (spans.isNotEmpty()) {
            spans.first().second
        } else {
            null
        }
    }

    private fun TextLine.lastAttribs(): TextAttributes? {
        return if (spans.isNotEmpty()) {
            spans.last().second
        } else {
            null
        }
    }

    private fun TextLine.before(charIndex: Int): TextLine {
        val newSpans = mutableListOf<Pair<String, TextAttributes>>()
        var i = 0
        var spanI = 0
        while (spanI < spans.size && i + spans[spanI].first.length < charIndex) {
            newSpans += spans[spanI]
            i += spans[spanI].first.length
            spanI++
        }
        newSpans += spans[spanI].before(charIndex - i)
        return TextLine(sanitize(newSpans))
    }

    private fun TextLine.after(charIndex: Int): TextLine {
        val newSpans = mutableListOf<Pair<String, TextAttributes>>()
        var i = 0
        var spanI = 0
        while (spanI < spans.size && i + spans[spanI].first.length < charIndex) {
            i += spans[spanI].first.length
            spanI++
        }
        if (spanI < spans.size) {
            newSpans += spans[spanI].after(charIndex - i)
            for (j in spanI + 1 until spans.size) {
                newSpans += spans[j]
            }
        }
        return TextLine(sanitize(newSpans))
    }

    fun TextLine.append(text: String): TextLine {
        val newSpans = mutableListOf<Pair<String, TextAttributes>>()
        newSpans += spans
        newSpans[spans.lastIndex] = spans.last().append(text)
        return TextLine(sanitize(newSpans))
    }

    fun Pair<String, TextAttributes>.before(index: Int): Pair<String, TextAttributes> {
        return first.substring(0, index) to second
    }

    fun Pair<String, TextAttributes>.after(index: Int): Pair<String, TextAttributes> {
        return first.substring(index) to second
    }

    fun Pair<String, TextAttributes>.append(text: String): Pair<String, TextAttributes> {
        return (first + text) to second
    }

    fun sanitize(spans: List<Pair<String, TextAttributes>>): List<Pair<String, TextAttributes>> {
        val newSpans = mutableListOf<Pair<String, TextAttributes>>()
        if (spans.isNotEmpty()) {
            var prevSpan = spans[0]
            newSpans += prevSpan
            for (i in 1 until spans.size) {
                val span = spans[i]
                if (span.second == prevSpan.second) {
                    prevSpan = prevSpan.append(span.first)
                    newSpans[newSpans.lastIndex] = prevSpan
                } else if (span.first.isNotEmpty()) {
                    prevSpan = span
                    newSpans += span
                }
            }
        }
        if (newSpans.size > 1 && newSpans[0].first.isEmpty()) {
            newSpans.removeAt(0)
        }
        return newSpans
    }
}