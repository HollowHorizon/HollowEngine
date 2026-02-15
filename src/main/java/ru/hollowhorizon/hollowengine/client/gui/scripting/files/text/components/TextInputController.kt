package ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.components

import de.fabmax.kool.Clipboard
import de.fabmax.kool.input.KeyEvent
import de.fabmax.kool.input.KeyboardInput
import de.fabmax.kool.input.UniversalKeyCode
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.UndoRedoHandler
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.util.ScriptTextAreaModifier
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.util.TextEditorHandler
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.util.TextLineProvider
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.util.bracketPairs

class TextInputController(
    private val modifier: ScriptTextAreaModifier,
    private val selectionController: TextSelectionController,
    private val lineProvider: () -> TextLineProvider,
    private val requestFocusNone: () -> Unit,
    private val editText: (String) -> Unit,
    private val applyBrackets: (String, Char) -> Unit,
    private val handleEnter: () -> Unit,
    private val indentSelection: () -> Unit,
    private val unindentSelection: () -> Unit,
    private val applyCompletion: () -> Unit,
) {
    fun onKeyEvent(keyEvent: KeyEvent) {
        if (keyEvent.isCharTyped) {
            handleCharTyped(keyEvent)
        } else if (keyEvent.isPressed) {
            handleKeyPress(keyEvent)
        } else if (keyEvent.isReleased) {
            handleKeyRelease(keyEvent)
        }
    }

    private fun handleCharTyped(keyEvent: KeyEvent) {
        val char = keyEvent.typedChar.toString()
        val closing = bracketPairs[keyEvent.localKeyCode.code.toChar()]

        if (closing == null) {
            editText(char)
        } else {
            applyBrackets(char, closing)
        }
    }

    private fun handleKeyPress(keyEvent: KeyEvent) {
        when (keyEvent.keyCode) {
            KeyboardInput.KEY_BACKSPACE -> handleBackspace(keyEvent)
            KeyboardInput.KEY_DEL -> handleDelete(keyEvent)
            KeyboardInput.KEY_ENTER, KeyboardInput.KEY_NP_ENTER -> handleEnter()
            KeyboardInput.KEY_ESC -> {
                selectionController.clearSelection()
                requestFocusNone()
                modifier.completions.clear()
            }

            KeyboardInput.KEY_CURSOR_LEFT, KeyboardInput.KEY_CURSOR_RIGHT,
            KeyboardInput.KEY_CURSOR_UP, KeyboardInput.KEY_CURSOR_DOWN,
            KeyboardInput.KEY_PAGE_UP, KeyboardInput.KEY_PAGE_DOWN,
            KeyboardInput.KEY_HOME, KeyboardInput.KEY_END,
            KeyboardInput.KEY_TAB,
                -> handleNavigation(keyEvent)

            else -> handleShortcuts(keyEvent)
        }
    }

    private fun handleShortcuts(keyEvent: KeyEvent) {
        if (keyEvent.isCtrlDown) {
            when (keyEvent.keyCode) {
                KEY_CODE_SELECT_ALL -> selectionController.selectAll()
                KEY_CODE_PASTE -> Clipboard.getStringFromClipboard { it?.let { editText(it) } }
                KEY_CODE_COPY -> selectionController.copySelection()?.let { Clipboard.copyToClipboard(it) }
                KEY_CODE_CUT -> selectionController.copySelection()?.let {
                    Clipboard.copyToClipboard(it)
                    editText("")
                }

                KEY_CODE_UNDO -> {
                    val provider = lineProvider()
                    if (keyEvent.isShiftDown) {
                        (provider as? UndoRedoHandler)?.redo { sl, el, sc, ec ->
                            selectionController.selectionChanged(sl, el, sc, ec)
                        }
                    } else {
                        (provider as? UndoRedoHandler)?.undo { sl, el, sc, ec ->
                            selectionController.selectionChanged(sl, el, sc, ec)
                        }
                    }
                }

                else -> {}
            }
        } else {
            if (modifier.completions.isNotEmpty()) {
                when (keyEvent.keyCode) {
                    UniversalKeyCode(' ') -> {
                    }

                    else -> {}
                }
            }
        }
    }

    private fun handleBackspace(keyEvent: KeyEvent) {
        if (selectionController.isEmptySelection) {
            selectionController.moveCaretLeft(wordWise = keyEvent.isCtrlDown, select = true)
        }

        val startChar = selectionController.caretLine?.text?.getOrNull(modifier.selectionCaretChar)
        editText("")
        val nextChar = selectionController.caretLine?.text?.getOrNull(modifier.selectionCaretChar)

        bracketPairs[startChar]?.let { closing ->
            if (nextChar == closing) {
                modifier.editorHandler?.replaceText(
                    selectionController.selectionCaretLine, selectionController.selectionCaretLine,
                    selectionController.selectionCaretChar, selectionController.selectionCaretChar + 1,
                    ""
                )
            }
        }
    }

    private fun handleDelete(keyEvent: KeyEvent) {
        if (selectionController.isEmptySelection) {
            selectionController.moveCaretRight(wordWise = keyEvent.isCtrlDown, select = true)
        }
        editText("")
    }

    private fun handleNavigation(keyEvent: KeyEvent) {
        val isShift = keyEvent.isShiftDown
        val isCtrl = keyEvent.isCtrlDown

        if (modifier.completions.isNotEmpty() && !isCtrl) {
            when (keyEvent.keyCode) {
                KeyboardInput.KEY_CURSOR_UP -> {
                    modifier.setCompletionIndex((modifier.completionIndex - 1 + modifier.completions.size) % modifier.completions.size)
                    return
                }

                KeyboardInput.KEY_CURSOR_DOWN -> {
                    modifier.setCompletionIndex((modifier.completionIndex + 1) % modifier.completions.size)
                    return
                }

                KeyboardInput.KEY_ENTER, KeyboardInput.KEY_NP_ENTER -> {
                    applyCompletion()
                    return
                }

                else -> {}
            }
        }

        when (keyEvent.keyCode) {
            KeyboardInput.KEY_CURSOR_LEFT -> selectionController.moveCaretLeft(wordWise = isCtrl, select = isShift)
            KeyboardInput.KEY_CURSOR_RIGHT -> selectionController.moveCaretRight(wordWise = isCtrl, select = isShift)
            KeyboardInput.KEY_CURSOR_UP -> selectionController.moveCaretLineUp(select = isShift)
            KeyboardInput.KEY_CURSOR_DOWN -> selectionController.moveCaretLineDown(select = isShift)
            KeyboardInput.KEY_PAGE_UP -> selectionController.moveCaretPageUp(select = isShift)
            KeyboardInput.KEY_PAGE_DOWN -> selectionController.moveCaretPageDown(select = isShift)
            KeyboardInput.KEY_HOME -> selectionController.moveCaretLineStart(select = isShift)
            KeyboardInput.KEY_END -> selectionController.moveCaretLineEnd(select = isShift)
            else -> {}
        }
    }

    private fun handleKeyRelease(keyEvent: KeyEvent) {
        if (keyEvent.keyCode == KeyboardInput.KEY_TAB) {
            if (modifier.completions.isNotEmpty()) {
                applyCompletion()
                return
            }
            if (keyEvent.isShiftDown) unindentSelection() else indentSelection()
        }
    }

    private companion object {
        val KEY_CODE_SELECT_ALL = UniversalKeyCode('A')
        val KEY_CODE_CUT = UniversalKeyCode('X')
        val KEY_CODE_COPY = UniversalKeyCode('C')
        val KEY_CODE_PASTE = UniversalKeyCode('V')
        val KEY_CODE_UNDO = UniversalKeyCode('Z')
    }
}
