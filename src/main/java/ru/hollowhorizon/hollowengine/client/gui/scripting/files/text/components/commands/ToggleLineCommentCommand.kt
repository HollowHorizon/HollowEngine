package ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.components.commands

import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.components.Command
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.components.CommandKey
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.components.EditorCommandContext

class ToggleLineCommentCommand : Command {
    override fun execute(c: EditorCommandContext): Boolean {
        val fromLine = c.selection.selectionFromLine
        val toLine = c.selection.selectionToLine

        if (c.lineProvider.size == 0) return false
        if (fromLine !in 0 until c.lineProvider.size) return false

        for (line in fromLine..toLine.coerceAtMost(c.lineProvider.lastIndex)) {
            val text = c.lineProvider[line].text
            val indentSize = text.indexOfFirst { it != ' ' }.let { if (it == -1) 0 else it }
            val afterIndent = text.drop(indentSize)

            if (afterIndent.startsWith("//")) {
                val removeFrom = indentSize
                var removeTo = (indentSize + 2).coerceAtMost(text.length)
                if (text.getOrNull(removeTo) == ' ') removeTo += 1

                c.inputController.replaceText(line, line, removeFrom, removeTo, "")
            } else {
                c.inputController.insertText(line, indentSize, "//")
            }
        }

        return true
    }

    companion object Key : CommandKey
}
