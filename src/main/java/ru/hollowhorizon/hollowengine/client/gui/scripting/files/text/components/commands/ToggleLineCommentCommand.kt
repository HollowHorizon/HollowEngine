package ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.components.commands

import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.components.Command
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.components.CommandKey
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.components.EditorCommandContext

class ToggleLineCommentCommand : Command {
    override fun execute(c: EditorCommandContext): Boolean {
        val sl = c.selection.selectionStartLine
        val sc = c.selection.selectionStartChar
        if (c.lineProvider.size == 0 || sl >= c.lineProvider.size) return false
        val text = c.lineProvider[sl].text
        val trimmed = text.trimStart()

        if (trimmed.startsWith("//")) {
            val commentIndex = trimmed.indexOf("//")
            c.inputController.replaceText(sl, sl, commentIndex, commentIndex + 2, "")
        } else {
            val indentSize = text.indexOfFirst { it != ' ' }.let { if (it == -1) 0 else it }
            c.inputController.insertText(sl, indentSize, "//")
        }

        return true
    }

    companion object Key : CommandKey
}
