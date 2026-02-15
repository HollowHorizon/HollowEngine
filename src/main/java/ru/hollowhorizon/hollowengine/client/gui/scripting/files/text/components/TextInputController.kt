package ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.components

import de.fabmax.kool.input.KeyEvent
import de.fabmax.kool.input.KeyboardInput
import de.fabmax.kool.math.Vec2i
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.UndoRedoHandler
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.components.commands.EditorDefaultCommands
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.components.keymap.EditorDefaultKeys
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.components.keymap.KeyMap
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.util.ScriptTextAreaModifier
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.util.TextEditorHandler
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.util.TextLineProvider
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.util.bracketPairs

class TextInputController(
    private val modifier: ScriptTextAreaModifier,
    private val selectionController: TextSelectionController,
    private val lineProvider: () -> TextLineProvider,
    private val completionsListState: () -> de.fabmax.kool.modules.ui2.LazyListState?,
    private val requestFocusNone: () -> Unit,
    private val applyBrackets: (String, Char) -> Unit,
    private val handleEnter: () -> Unit,
    private val applyCompletion: () -> Unit,
) : TextEditorHandler {

    private val completionManager = CompletionManager(modifier, completionsListState)

    init {
        EditorDefaultCommands.ensureRegistered()
        EditorDefaultKeys.ensureRegistered()
    }

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
        if (tryExecuteKeyBinding(keyEvent)) return

        val char = keyEvent.typedChar.toString()
        val closing = bracketPairs[keyEvent.localKeyCode.code.toChar()]

        if (closing == null) {
            editText(char)
        } else {
            applyBrackets(char, closing)
        }
    }

    private fun handleKeyPress(keyEvent: KeyEvent) {
        if (tryExecuteKeyBinding(keyEvent)) return

        when (keyEvent.keyCode) {
            KeyboardInput.KEY_BACKSPACE -> handleBackspace(keyEvent)
            KeyboardInput.KEY_DEL -> handleDelete(keyEvent)
            KeyboardInput.KEY_ENTER, KeyboardInput.KEY_NP_ENTER -> handleEnter()
            KeyboardInput.KEY_ESC -> {
                selectionController.clearSelection()
                requestFocusNone()
                completionManager.close()
            }

            KeyboardInput.KEY_CURSOR_LEFT, KeyboardInput.KEY_CURSOR_RIGHT,
            KeyboardInput.KEY_CURSOR_UP, KeyboardInput.KEY_CURSOR_DOWN,
            KeyboardInput.KEY_PAGE_UP, KeyboardInput.KEY_PAGE_DOWN,
            KeyboardInput.KEY_HOME, KeyboardInput.KEY_END,
            KeyboardInput.KEY_TAB,
                -> handleNavigation(keyEvent)

            else -> {
            }
        }
    }

    private fun tryExecuteKeyBinding(event: KeyEvent): Boolean {
        val provider = lineProvider()
        val ctx = EditorCommandContext(
            event = event,
            selection = selectionController,
            lineProvider = provider,
            inputController = this,
            historyManager = provider as UndoRedoHandler,
            hasCompletions = modifier.completions.isNotEmpty(),
            completion = completionManager,
        )
        val commandKey = KeyMap.resolve(event, ctx) ?: return false
        return CommandRegistry.execute(commandKey, ctx)
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
        val provider = lineProvider()

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

        completionManager.onCaretMoved(provider)
    }

    fun applyCompletion() {
        applyCompletion.invoke()
    }

    fun clearCompletions() {
        completionManager.close()
    }

    private fun handleKeyRelease(keyEvent: KeyEvent) {
        tryExecuteKeyBinding(keyEvent)
    }

    fun editText(text: String) {
        val editor = modifier.editorHandler ?: return
        val caretPos = if (selectionController.isEmptySelection) {
            editor.insertText(selectionController.selectionCaretLine, selectionController.selectionCaretChar, text)
        } else {
            editor.replaceText(
                selectionController.selectionFromLine,
                selectionController.selectionToLine,
                selectionController.selectionFromChar,
                selectionController.selectionToChar,
                text
            )
        }
        selectionController.selectionChanged(caretPos.y, caretPos.y, caretPos.x, caretPos.x)
        completionManager.onCompletionsOpened(lineProvider())
    }

    // Вероятно в будущем лучше протянуть всю логику сюда напрямую, но там пока сложная логика для подсветки, так что пока её не трогал
    override fun insertText(line: Int, caret: Int, insertion: String): Vec2i {
        val pos = modifier.editorHandler?.insertText(line, caret, insertion) ?: Vec2i(caret, line)
        completionManager.onCompletionsOpened(lineProvider())
        return pos
    }

    override fun replaceText(
        selectionStartLine: Int,
        selectionEndLine: Int,
        selectionStartChar: Int,
        selectionEndChar: Int,
        replacement: String,
    ): Vec2i {
        val pos = modifier.editorHandler?.replaceText(
            selectionStartLine,
            selectionEndLine,
            selectionStartChar,
            selectionEndChar,
            replacement
        ) ?: Vec2i(selectionStartChar, selectionEndChar)
        completionManager.onCompletionsOpened(lineProvider())
        return pos
    }
}
