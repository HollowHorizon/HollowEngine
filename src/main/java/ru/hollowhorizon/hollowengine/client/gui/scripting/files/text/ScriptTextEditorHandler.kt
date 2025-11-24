package ru.hollowhorizon.hollowengine.client.gui.scripting.files.text

import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.util.ScriptTextLine


data class UndoableAction(
    val startLine: Int,
    val startChar: Int,
    val caretLine: Int,
    var caretChar: Int,
    val oldLines: List<ScriptTextLine>,
    var newLines: List<ScriptTextLine>,
    val canMerge: Boolean
)

interface UndoRedoHandler {
    fun undo(onSelectionChanged: ((Int, Int, Int, Int) -> Unit)?)
    fun redo(onSelectionChanged: ((Int, Int, Int, Int) -> Unit)?)
}