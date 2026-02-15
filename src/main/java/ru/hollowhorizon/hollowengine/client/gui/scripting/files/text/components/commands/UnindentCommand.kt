package ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.components.commands

import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.components.Command
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.components.CommandKey
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.components.EditorCommandContext
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.util.TextAreaConfig

class UnindentCommand : Command {
    override fun execute(c: EditorCommandContext): Boolean {
        if (c.selection.isEmptySelection) {
            val line = c.selection.selectionCaretLine
            val char = c.selection.selectionCaretChar
            val text = c.lineProvider[line].text

            val spacesToRemove = text.take(char).takeLastWhile { it == ' ' }.length
                .coerceAtMost(TextAreaConfig.INDENT_SIZE)

            if (spacesToRemove > 0) {
                c.inputController.replaceText(line, line, char - spacesToRemove, char, "")
                c.selection.selectionChanged(line, line, char - spacesToRemove, char - spacesToRemove)
            }
            return true
        }

        val fromLine = c.selection.selectionFromLine
        val toLine = c.selection.selectionToLine

        var removedAtStart = 0
        var removedAtEnd = 0

        for (line in fromLine..toLine) {
            val text = c.lineProvider[line].text
            val count = text.takeWhile { it == ' ' }.length.coerceAtMost(TextAreaConfig.INDENT_SIZE)

            if (count > 0) {
                c.inputController.replaceText(line, line, 0, count, "")
                if (line == fromLine) removedAtStart = count
                if (line == toLine) removedAtEnd = count
            }
        }

        val newFromChar = (c.selection.selectionFromChar - removedAtStart).coerceAtLeast(0)
        val newToChar = (c.selection.selectionToChar - removedAtEnd).coerceAtLeast(0)
        c.selection.selectionChanged(fromLine, toLine, newFromChar, newToChar)

        return true
    }

    companion object Key : CommandKey
}
