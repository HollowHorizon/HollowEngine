package ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.components.commands

import de.fabmax.kool.input.KeyboardInput
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.components.Command
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.components.CommandKey
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.components.EditorCommandContext
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.util.CompiledFileProvider

class GoToDefinitionCommand : Command {
    override fun canExecute(c: EditorCommandContext): Boolean {
        if (c.event == null) return false
        return c.event.isReleased && c.event.keyCode == KeyboardInput.KEY_F4
    }

    override fun execute(c: EditorCommandContext): Boolean {
        val ignored = c.inputController.modifier.editorHandler as? CompiledFileProvider ?: return false
        return true
    }

    companion object Key : CommandKey
}
