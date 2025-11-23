package ru.hollowhorizon.hollowengine.client.gui.scripting.files.text

import de.fabmax.kool.modules.ui2.TextLine

data class UndoableAction(
    val startLine: Int,
    val startChar: Int,
    val caretLine: Int,
    var caretChar: Int,
    val oldLines: List<TextLine>,
    var newLines: List<TextLine>,
    val canMerge: Boolean
)

interface UndoRedoHandler {
    fun undo(onSelectionChanged: ((Int, Int, Int, Int) -> Unit)?)
    fun redo(onSelectionChanged: ((Int, Int, Int, Int) -> Unit)?)
}