package ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.keys

import de.fabmax.kool.input.KeyboardInput
import ru.hollowhorizon.hollowengine.common.events.SubscribeEvent
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.util.CompiledFileProvider

private val GO_TO_DEFINITION = KeyboardInput.KEY_F4

@SubscribeEvent
fun onGoToDefinition(event: ScriptAreaKeyEvent) {
    val modifier = event.area.modifier
    val selectionHandler = modifier.editorHandler as? CompiledFileProvider ?: return
    if (event.isReleased && event.keyCode == GO_TO_DEFINITION) {
        val line = modifier.selectionStartLine
        val column = modifier.selectionStartChar

//        val (file, offset) = selectionHandler.recover(Position(line, column))
//
//        val location = goToDefinition(file, offset, KotlinLanguageServer.classPath)
//
//        if (location != null && selectionHandler.file.toPath() == Paths.get(location.uri)) {
//            val position = location.range.start
//            modifier.onSelectionChanged?.invoke(position.line, position.line, position.character, position.character)
//            event.area.selectionHandler.selectionChanged(
//                position.line,
//                position.line,
//                position.character,
//                position.character
//            )
//        }

        event.isCanceled = true
    }
}