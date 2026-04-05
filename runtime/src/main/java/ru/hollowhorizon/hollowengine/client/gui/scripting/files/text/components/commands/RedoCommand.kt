package ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.components.commands

import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.components.Command
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.components.CommandKey
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.components.EditorCommandContext

class RedoCommand : Command {
    override fun execute(c: EditorCommandContext): Boolean {
        c.historyManager.redo { sl, el, sc, ec ->
            c.selection.selectionChanged(sl, el, sc, ec)
        }
        return true
    }

    companion object Key : CommandKey
}
