package ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.components

import de.fabmax.kool.math.MutableVec2f
import de.fabmax.kool.math.clamp
import de.fabmax.kool.modules.ui2.Dp
import de.fabmax.kool.modules.ui2.Focusable
import de.fabmax.kool.modules.ui2.LazyListNode
import de.fabmax.kool.modules.ui2.PointerEvent
import ru.hollowhorizon.hollowengine.client.gui.scripting.EditorTheme
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.util.*
import kotlin.math.max
import kotlin.math.min

class TextSelectionController(
    private val owner: Focusable,
    private val modifier: ScriptTextAreaModifier,
    private val lineProvider: () -> TextLineProvider,
    private val linesHolder: () -> LazyListNode,
    private val requestFocus: () -> Unit,
    private val isFocused: () -> Boolean,
) {
    var isSelecting = false

    var selectionStartLine = 0
    var selectionCaretLine = 0
    var selectionStartChar = 0
    var selectionCaretChar = 0

    val caretLine: ScriptTextLine?
        get() = lineProvider().let { lp -> if (selectionCaretLine in 0 until lp.size) lp[selectionCaretLine] else null }

    private var caretLineScope: AttributedTextScope? = null

    val isReverseSelection: Boolean
        get() = selectionCaretLine < selectionStartLine
    val isEmptySelection: Boolean
        get() = selectionStartLine == selectionCaretLine && selectionStartChar == selectionCaretChar

    val selectionFromLine: Int
        get() = min(selectionStartLine, selectionCaretLine)
    val selectionToLine: Int
        get() = max(selectionStartLine, selectionCaretLine)

    val selectionFromChar: Int
        get() = when {
            isReverseSelection -> selectionCaretChar
            selectionStartLine == selectionCaretLine -> min(selectionStartChar, selectionCaretChar)
            else -> selectionStartChar
        }

    val selectionToChar: Int
        get() = when {
            isReverseSelection -> selectionStartChar
            selectionStartLine == selectionCaretLine -> max(selectionStartChar, selectionCaretChar)
            else -> selectionCaretChar
        }

    fun updateSelectionRange() {
        selectionStartLine = modifier.selectionStartLine
        selectionCaretLine = modifier.selectionCaretLine
        selectionStartChar = modifier.selectionStartChar
        selectionCaretChar = modifier.selectionCaretChar
        caretLineScope = null
    }

    fun applySelectionRange(attributedText: AttributedTextScope, line: ScriptTextLine, lineIndex: Int) {
        val from = selectionFromLine
        val to = selectionToLine

        var selCaretPos = 0
        var selStartPos = 0

        if (lineIndex in (from + 1) until to) {
            selStartPos = 0
            selCaretPos = line.length
        } else if (lineIndex == selectionStartLine && selectionStartLine == selectionCaretLine) {
            selStartPos = selectionStartChar
            selCaretPos = selectionCaretChar
        } else if (lineIndex == selectionFromLine) {
            if (isReverseSelection) {
                selStartPos = line.length
                selCaretPos = selectionCaretChar
            } else {
                selStartPos = selectionStartChar
                selCaretPos = line.length
            }
        } else if (lineIndex == selectionToLine) {
            if (isReverseSelection) {
                selStartPos = selectionStartChar
                selCaretPos = 0
            } else {
                selStartPos = 0
                selCaretPos = selectionCaretChar
            }
        }

        if (lineIndex == selectionCaretLine) {
            caretLineScope = attributedText
        }

        val isMultiLineSelection = from != to
        val hasSelection = (selStartPos != selCaretPos) ||
            (lineIndex in (from + 1) until to) ||
            (isMultiLineSelection && lineIndex == from)

        attributedText.modifier.selectionColor = EditorTheme.selection
        attributedText.modifier.caretColor = EditorTheme.caret

        attributedText.modifier.selectionRange(selStartPos, selCaretPos, hasSelection, isMultiLineSelection)
            .isCaretVisible(isFocused() && lineIndex == selectionCaretLine)
    }

    fun copySelection(): String? {
        val lp = lineProvider()
        return if (isEmptySelection) {
            null
        } else if (selectionStartLine == selectionCaretLine) {
            val fromChar = min(selectionStartChar, selectionCaretChar)
            val toChar = max(selectionStartChar, selectionCaretChar)
            lp[selectionFromLine].text.substring(fromChar, toChar)
        } else {
            buildString {
                append(lp[selectionFromLine].text.substring(selectionFromChar)).append('\n')
                for (i in (selectionFromLine + 1) until selectionToLine) {
                    append(lp[i].text).append('\n')
                }
                append(lp[selectionToLine].text.substring(0, selectionToChar))
            }
        }
    }

    fun clearSelection() {
        selectionChanged(selectionCaretLine, selectionCaretLine, selectionCaretChar, selectionCaretChar, false)
    }

    fun selectAll() {
        val lp = lineProvider()
        if (lp.size <= 0) return
        selectionChanged(0, lp.lastIndex, 0, lp[lp.lastIndex].length, false)
    }

    fun selectWord(attributedText: AttributedTextScope, text: String, lineIndex: Int, ev: PointerEvent) {
        val charIndex = attributedText.charIndexFromLocalX(ev.position.x)
        val startChar = TextCaretNavigation.startOfExpression(text, charIndex)
        val caretChar = TextCaretNavigation.endOfExpression(text, charIndex)
        caretLineScope = attributedText
        selectionChanged(lineIndex, lineIndex, startChar, caretChar)
    }

    fun selectLine(attributedText: AttributedTextScope, text: String, lineIndex: Int) {
        selectionChanged(lineIndex, lineIndex, 0, text.length)
        caretLineScope = attributedText
    }

    fun onSelectStart(attributedText: AttributedTextScope, lineIndex: Int, ev: PointerEvent, isSelecting: Boolean) {
        requestFocus()

        this.isSelecting = isSelecting
        val charIndex = attributedText.charIndexFromLocalX(ev.position.x)
        caretLineScope = attributedText
        selectionChanged(lineIndex, lineIndex, charIndex, charIndex)
    }

    fun onDrag(ev: PointerEvent) {
        caretLineScope?.apply {
            val dragLocalPos = MutableVec2f()
            uiNode.toLocal(ev.screenPosition, dragLocalPos)
            val charIndex = charIndexFromLocalX(dragLocalPos.x)
            selectionChanged(selectionStartLine, selectionCaretLine, selectionStartChar, charIndex, false)
        }
    }

    fun onSelectEnd() {
        isSelecting = false
    }

    fun onPointer(attributedText: AttributedTextScope, lineIndex: Int, ev: PointerEvent) {
        if (isSelecting && ev.pointer.isDrag) {
            caretLineScope = attributedText
            selectionChanged(selectionStartLine, lineIndex, selectionStartChar, selectionCaretChar, false)
        }
    }

    fun moveCaretLeft(wordWise: Boolean, select: Boolean) {
        val lp = lineProvider()
        caretLine?.text?.let { txt ->
            if (selectionCaretChar == 0 && selectionCaretLine > 0) {
                selectionCaretLine--
                val line = lp[selectionCaretLine]
                val newTxt = line.text
                selectionCaretChar = line.length

                if (wordWise) {
                    if (newTxt.isEmpty()) return
                    selectionCaretChar = TextCaretNavigation.moveExpressionLeft(newTxt, selectionCaretChar)
                }
                if (!select) {
                    selectionStartLine = selectionCaretLine
                    selectionStartChar = selectionCaretChar
                }
            } else if (wordWise) {
                selectionCaretChar = TextCaretNavigation.moveExpressionLeft(txt, selectionCaretChar)
            } else {
                selectionCaretChar = (selectionCaretChar - 1).clamp(0, txt.length)
            }
        }
        if (!select) {
            selectionStartLine = selectionCaretLine
            selectionStartChar = selectionCaretChar
        }
        selectionChanged(selectionStartLine, selectionCaretLine, selectionStartChar, selectionCaretChar)
    }

    fun moveCaretRight(wordWise: Boolean, select: Boolean) {
        val lp = lineProvider()
        caretLine?.text?.let { txt ->
            if (selectionCaretChar == txt.length && selectionCaretLine < lp.lastIndex) {
                selectionCaretLine++
                val line = lp[selectionCaretLine]
                val newTxt = line.text
                selectionCaretChar = 0

                if (wordWise) {
                    selectionCaretChar = TextCaretNavigation.moveExpressionRight(newTxt, selectionCaretChar)
                }
                if (!select) {
                    selectionStartLine = selectionCaretLine
                    selectionStartChar = selectionCaretChar
                }
            } else if (wordWise) {
                selectionCaretChar = TextCaretNavigation.moveExpressionRight(txt, selectionCaretChar)
            } else {
                selectionCaretChar = (selectionCaretChar + 1).clamp(0, txt.length)
            }
        }
        if (!select) {
            selectionStartLine = selectionCaretLine
            selectionStartChar = selectionCaretChar
        }
        selectionChanged(selectionStartLine, selectionCaretLine, selectionStartChar, selectionCaretChar)
    }

    fun moveCaretLineUp(select: Boolean) {
        if (modifier.editorConfig.singleLine) return
        moveCaretToLine(selectionCaretLine - 1, select)
    }

    fun moveCaretLineDown(select: Boolean) {
        if (modifier.editorConfig.singleLine) return
        moveCaretToLine(selectionCaretLine + 1, select)
    }

    fun moveCaretPageUp(select: Boolean) {
        if (modifier.editorConfig.singleLine) return
        val bottomLinePad = 2
        val numPageLines = max(1, linesHolder().state.numVisibleItems - bottomLinePad)
        moveCaretToLine(selectionCaretLine - numPageLines, select)
    }

    fun moveCaretPageDown(select: Boolean) {
        if (modifier.editorConfig.singleLine) return
        val bottomLinePad = 2
        val numPageLines = max(1, linesHolder().state.numVisibleItems - bottomLinePad)
        moveCaretToLine(selectionCaretLine + numPageLines, select)
    }

    private fun moveCaretToLine(targetLine: Int, select: Boolean) {
        if (modifier.editorConfig.singleLine) return
        val lp = lineProvider()
        val line = caretLine ?: return
        val caretX = line.charIndexToPx(selectionCaretChar)

        if (targetLine in 0 until lp.size) {
            selectionCaretChar = lp[targetLine].charIndexFromPx(caretX)
            selectionCaretLine = targetLine
        } else if (targetLine < 0) {
            selectionCaretChar = 0
            selectionCaretLine = 0
        } else if (targetLine > lp.lastIndex) {
            selectionCaretChar = lp[lp.lastIndex].length
            selectionCaretLine = lp.lastIndex
        }

        if (!select) {
            selectionStartLine = selectionCaretLine
            selectionStartChar = selectionCaretChar
        }
        selectionChanged(selectionStartLine, selectionCaretLine, selectionStartChar, selectionCaretChar)
    }

    fun moveCaretLineStart(select: Boolean) {
        selectionCaretChar = 0
        if (!select) {
            selectionStartLine = selectionCaretLine
            selectionStartChar = selectionCaretChar
        }
        selectionChanged(selectionStartLine, selectionCaretLine, selectionStartChar, selectionCaretChar)
    }

    fun moveCaretLineEnd(select: Boolean) {
        val line = caretLine ?: return
        selectionCaretChar = line.length
        if (!select) {
            selectionStartLine = selectionCaretLine
            selectionStartChar = selectionCaretChar
        }
        selectionChanged(selectionStartLine, selectionCaretLine, selectionStartChar, selectionCaretChar)
    }

    fun selectionChanged(
        startLine: Int,
        caretLine: Int,
        startChar: Int,
        caretChar: Int,
        scrollToCaret: Boolean = true,
    ) {
        selectionStartLine = startLine
        selectionCaretLine = caretLine
        selectionStartChar = startChar
        selectionCaretChar = caretChar

        if (startLine != modifier.selectionStartLine || caretLine != modifier.selectionCaretLine || startChar != modifier.selectionStartChar || caretChar != modifier.selectionCaretChar) {
            modifier.setSelectionRange(startLine, caretLine, startChar, caretChar)
            resetCaretBlinkState()
            if (scrollToCaret) {
                scrollToCaret()
            }
        }
    }

    fun resetCaretBlinkState() {
        (caretLineScope as? AttributedTextNode)?.resetCaretBlinkState()
    }

    fun scrollToCaret() {
        val scrState = linesHolder().state
        scrState.scrollToItem.set(selectionCaretLine)

        val scrollPad = 16f
        val caretX = Dp.fromPx(caretLine?.charIndexToPx(selectionCaretChar) ?: 0f).value
        val scrLt = scrState.xScrollDp.value
        val scrRt = scrState.xScrollDp.value + scrState.viewWidthDp.value
        if (caretX - scrollPad < scrLt) {
            scrState.scrollDpX(caretX - scrLt - scrollPad)
        } else if (caretX + scrollPad * 4 > scrRt) {
            scrState.scrollDpX(caretX - scrRt + scrollPad * 4)
        }
    }
}
