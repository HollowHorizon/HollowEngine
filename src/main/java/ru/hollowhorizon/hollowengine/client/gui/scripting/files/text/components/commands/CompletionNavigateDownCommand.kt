package ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.components.commands

import de.fabmax.kool.input.KeyboardInput
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.components.Command
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.components.CommandKey
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.components.EditorCommandContext

class CompletionNavigateDownCommand : Command {
    override fun canExecute(c: EditorCommandContext): Boolean {
        return c.hasCompletions && !c.event.isCtrlDown && c.event.keyCode == KeyboardInput.KEY_CURSOR_DOWN
    }

    override fun execute(c: EditorCommandContext): Boolean {
        val mgr = c.completion ?: return false
        val idx = mgr.index()
        val size = mgr.size()
        if (size <= 0) return false

        val next = if (idx < size - 1) idx + 1 else 0
        mgr.setIndex(next)
        mgr.scrollTo(next)
        return true
    }

    companion object Key : CommandKey
}
