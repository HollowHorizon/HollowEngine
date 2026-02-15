package ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.components.commands

import de.fabmax.kool.input.KeyboardInput
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.components.Command
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.components.CommandKey
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.components.EditorCommandContext

class CompletionNavigateUpCommand : Command {
    override fun canExecute(c: EditorCommandContext): Boolean {
        return c.hasCompletions && !c.event.isCtrlDown && c.event.keyCode == KeyboardInput.KEY_CURSOR_UP
    }

    override fun execute(c: EditorCommandContext): Boolean {
        val idx = c.getCompletionIndex()
        val size = c.getCompletionsSize()
        if (size <= 0) return false

        val next = if (idx > 0) idx - 1 else size - 1
        c.setCompletionIndex(next)
        c.completionsListState?.scrollToItem?.set(next)
        return true
    }

    companion object Key : CommandKey
}
