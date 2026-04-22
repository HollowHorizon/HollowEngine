package ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.components.commands

import de.fabmax.kool.input.UniversalKeyCode
import de.fabmax.kool.util.logW
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.components.Command
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.components.CommandKey
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.components.EditorCommandContext
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.fullText
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.util.CompiledFileProvider

class ReformatCommand : Command {
    override fun canExecute(c: EditorCommandContext): Boolean {
        val e = c.event ?: return false
        return e.isReleased && e.isCtrlDown && e.isAltDown && e.keyCode == UniversalKeyCode('l')
    }

    override fun execute(c: EditorCommandContext): Boolean {
        val editorHandler = c.inputController.modifier.editorHandler as? CompiledFileProvider ?: return false

        return try {
            val original = c.lineProvider.fullText()
            val formatted = original //TODO Formatter.format(KOTLINLANG_FORMAT, original)
            if (original == formatted) return false
            editorHandler.setText(formatted)
            true
        } catch (ex: Exception) {
            c.inputController.logW { ex.stackTraceToString() }
            false
        }
    }

    companion object Key : CommandKey
}
