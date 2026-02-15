package ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.components.commands

import de.fabmax.kool.Clipboard
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.components.Command
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.components.CommandKey
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.components.EditorCommandContext

class PasteCommand : Command {
    override fun execute(c: EditorCommandContext): Boolean {
        Clipboard.getStringFromClipboard { it?.let { c.inputController.editText(it) } }
        return true
    }

    companion object Key : CommandKey
}
