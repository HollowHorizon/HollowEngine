package ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.components.commands

import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.components.Command
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.components.CommandKey
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.components.EditorCommandContext

class ApplyCompletionCommand : Command {
    override fun canExecute(c: EditorCommandContext): Boolean {
        return c.hasCompletions
    }

    override fun execute(c: EditorCommandContext): Boolean {
        c.inputController.applyCompletion()
        return true
    }

    companion object Key : CommandKey
}
