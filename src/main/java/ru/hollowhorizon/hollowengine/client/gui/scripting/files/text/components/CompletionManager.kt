package ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.components

import de.fabmax.kool.modules.ui2.LazyListState
import de.fabmax.kool.modules.ui2.MutableStateValue
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.util.CompiledFileProvider
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.util.ScriptTextAreaModifier
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.util.TextCaretNavigation
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.util.TextLineProvider

class CompletionManager(
    private val modifier: ScriptTextAreaModifier,
    private val completionIndex: () -> MutableStateValue<Int>,
    private val completionsListState: () -> LazyListState?,
) {
    private var anchorLine: Int = -1
    private var anchorFromChar: Int = -1
    private var anchorToChar: Int = -1

    val isOpen: Boolean
        get() = modifier.completions.isNotEmpty()

    fun size(): Int = modifier.completions.size

    fun index(): Int = completionIndex().value

    fun setIndex(index: Int) {
        completionIndex().set(index)
    }

    fun close() {
        modifier.completions.clear()
        completionIndex().set(0)
        (modifier.editorHandler as? CompiledFileProvider)?.analysisState?.completions?.clear()
        anchorLine = -1
        anchorFromChar = -1
        anchorToChar = -1
    }

    fun scrollTo(index: Int) {
        completionsListState()?.scrollToItem?.set(index)
    }

    fun onCompletionsOpened(lineProvider: TextLineProvider) {
        if (!isOpen) return
        val line = modifier.selectionCaretLine
        if (line !in 0 until lineProvider.size) return

        val text = lineProvider[line].text
        // Не открываем автодополнение для пустых строк (кроме случая, когда это действительно нужно)
        if (text.isEmpty()) {
            // Закрываем автодополнение для пустых строк, чтобы избежать ложных срабатываний
            close()
            return
        }
        
        val caret = modifier.selectionCaretChar.coerceIn(0, text.length)
        val idx = (caret - 1).coerceAtLeast(0)

        anchorLine = line
        anchorFromChar = TextCaretNavigation.startOfExpression(text, idx)
        anchorToChar = TextCaretNavigation.endOfExpression(text, idx)
    }

    fun onCaretMoved(lineProvider: TextLineProvider) {
        if (!isOpen) return
        if (anchorLine < 0) {
            onCompletionsOpened(lineProvider)
            return
        }

        val caretLine = modifier.selectionCaretLine
        if (caretLine != anchorLine) {
            close()
            return
        }

        val caretChar = modifier.selectionCaretChar
        if (caretChar !in anchorFromChar..anchorToChar) {
            close()
        }
    }
}
