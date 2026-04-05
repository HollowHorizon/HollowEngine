package ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.components.commands

import de.fabmax.kool.Clipboard
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.components.Command
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.components.CommandKey
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.components.EditorCommandContext

class CutCommand : Command {
    override fun execute(c: EditorCommandContext): Boolean {
        c.selection.copySelection()?.let {
            Clipboard.copyToClipboard(it)
            c.inputController.editText("")
        }
        return true
    }

    companion object Key : CommandKey
}
