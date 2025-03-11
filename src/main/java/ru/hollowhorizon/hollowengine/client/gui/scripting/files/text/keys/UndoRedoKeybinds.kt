package ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.keys

import de.fabmax.kool.input.LocalKeyCode
import ru.hollowhorizon.hc.common.events.SubscribeEvent
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.ScriptTextEditorHandler

private val UNDO_KEY = LocalKeyCode('z')

@SubscribeEvent
fun onUndoRedo(event: ScriptAreaKeyEvent) {
    val modifier = event.area.modifier
    val selectionHandler = modifier.editorHandler as? ScriptTextEditorHandler
    if (event.isReleased && event.isCtrlDown && event.localKeyCode == UNDO_KEY && selectionHandler != null) {
        if (event.isShiftDown) selectionHandler.redo(modifier.onSelectionChanged)
        else selectionHandler.undo(modifier.onSelectionChanged)
        return
    }
}