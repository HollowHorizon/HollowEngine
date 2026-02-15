package ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.components.commands

import de.fabmax.kool.input.KeyboardInput
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.components.Command
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.components.CommandKey
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.components.EditorCommandContext

class CompletionAcceptCommand : Command {
    override fun canExecute(c: EditorCommandContext): Boolean {
        return c.hasCompletions && !c.event.isCtrlDown &&
            (c.event.keyCode == KeyboardInput.KEY_ENTER || c.event.keyCode == KeyboardInput.KEY_NP_ENTER)
    }

    override fun execute(c: EditorCommandContext): Boolean {
        c.inputController.applyCompletion()
        return true
    }

    companion object Key : CommandKey
}
