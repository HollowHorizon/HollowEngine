package ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.components

import de.fabmax.kool.input.KeyEvent
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.UndoRedoHandler
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.util.TextLineProvider

class EditorCommandContext(
    val event: KeyEvent,
    val selection: TextSelectionController,
    val lineProvider: TextLineProvider,
    val inputController: TextInputController,
    val historyManager: UndoRedoHandler
)