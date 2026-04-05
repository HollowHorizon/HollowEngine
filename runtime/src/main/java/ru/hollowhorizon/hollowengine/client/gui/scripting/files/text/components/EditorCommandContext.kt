package ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.components

import de.fabmax.kool.input.KeyEvent
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.UndoRedoHandler
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.util.TextLineProvider
import ru.hollowhorizon.hollowengine.common.scripting.ide.CompletionItem

class EditorCommandContext(
    val event: KeyEvent?,
    val state: EditorState,
    val selection: TextSelectionController,
    val lineProvider: TextLineProvider,
    val inputController: TextInputController,
    val historyManager: UndoRedoHandler,
    val hasCompletions: Boolean,
    val completion: CompletionManager?,
) {
    var completionItem: CompletionItem? = inputController.modifier.completions.getOrNull(completion?.index() ?: 0)
    var bracketChar: String? = null
    var bracketClosing: Char? = null
    var importFqName: String? = null
}