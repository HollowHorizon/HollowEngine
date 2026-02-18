package ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.components.commands

import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.components.Command
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.components.CommandKey
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.components.EditorCommandContext

class IndentCommand : Command {
    override fun execute(c: EditorCommandContext): Boolean {
        val fromLine = c.selection.selectionFromLine
        val toLine = c.selection.selectionToLine
        val indentSize = c.state.config.indentSize

        if (fromLine == toLine && c.selection.isEmptySelection) {
            val caretLine = c.selection.selectionCaretLine
            val caretChar = c.selection.selectionCaretChar

            c.inputController.insertText(caretLine, caretChar, " ".repeat(indentSize))
            c.selection.selectionChanged(
                caretLine,
                caretLine,
                caretChar + indentSize,
                caretChar + indentSize
            )
            return true
        }

        for (line in fromLine..toLine) {
            c.inputController.insertText(line, 0, " ".repeat(indentSize))
        }

        val newFromChar = c.selection.selectionFromChar + indentSize
        val newToChar = c.selection.selectionToChar + indentSize
        c.selection.selectionChanged(fromLine, toLine, newFromChar, newToChar)

        return true
    }

    companion object Key : CommandKey
}
