package ru.hollowhorizon.hollowengine.client.gui.scripting.files.text

import de.fabmax.kool.modules.ui2.TextLine

data class UndoableAction(
    val startLine: Int,
    val caretLine: Int,
    val startChar: Int,
    val caretChar: Int,

    val numOldLines: Int,
    val oldLines: List<TextLine>,
    val newLines: List<TextLine>,
)

interface UndoRedoHandler {
    fun undo(onSelectionChanged: ((Int, Int, Int, Int) -> Unit)?)
    fun redo(onSelectionChanged: ((Int, Int, Int, Int) -> Unit)?)
}