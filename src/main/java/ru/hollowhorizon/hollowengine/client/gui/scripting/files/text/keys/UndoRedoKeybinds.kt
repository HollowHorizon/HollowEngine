package ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.keys

import de.fabmax.kool.input.KeyCode
import de.fabmax.kool.input.LocalKeyCode
import de.fabmax.kool.input.UniversalKeyCode
import ru.hollowhorizon.hc.common.events.SubscribeEvent
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.UndoRedoHandler

private val UNDO_KEY = UniversalKeyCode('Z')

@SubscribeEvent
fun onUndoRedo(event: ScriptAreaKeyEvent) {
    val modifier = event.area.modifier
    val selectionHandler = modifier.editorHandler as? UndoRedoHandler ?: return
    if (event.isReleased && event.isCtrlDown && event.keyCode == UNDO_KEY) {
        if (event.isShiftDown) selectionHandler.redo(modifier.onSelectionChanged)
        else selectionHandler.undo(modifier.onSelectionChanged)
        return
    }
}