package ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.components.commands

import de.fabmax.kool.input.KeyboardInput
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.components.Command
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.components.CommandKey
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.components.EditorCommandContext

class CompletionCancelCommand : Command {
    override fun canExecute(c: EditorCommandContext): Boolean {
        return c.hasCompletions && c.event?.keyCode == KeyboardInput.KEY_ESC
    }

    override fun execute(c: EditorCommandContext): Boolean {
        (c.completion ?: return false).close()
        return true
    }

    companion object Key : CommandKey
}
