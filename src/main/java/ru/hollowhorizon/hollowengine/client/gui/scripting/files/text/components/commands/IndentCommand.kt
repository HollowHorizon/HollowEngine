package ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.components.commands

import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.components.Command
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.components.CommandKey
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.components.EditorCommandContext
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.util.TextAreaConfig

class IndentCommand : Command {
    override fun execute(c: EditorCommandContext): Boolean {
        val fromLine = c.selection.selectionFromLine
        val toLine = c.selection.selectionToLine

        if (fromLine == toLine && c.selection.isEmptySelection) {
            val caretLine = c.selection.selectionCaretLine
            val caretChar = c.selection.selectionCaretChar

            c.inputController.insertText(caretLine, caretChar, " ".repeat(TextAreaConfig.INDENT_SIZE))
            c.selection.selectionChanged(
                caretLine,
                caretLine,
                caretChar + TextAreaConfig.INDENT_SIZE,
                caretChar + TextAreaConfig.INDENT_SIZE
            )
            return true
        }

        for (line in fromLine..toLine) {
            c.inputController.insertText(line, 0, " ".repeat(TextAreaConfig.INDENT_SIZE))
        }

        val newFromChar = c.selection.selectionFromChar + TextAreaConfig.INDENT_SIZE
        val newToChar = c.selection.selectionToChar + TextAreaConfig.INDENT_SIZE
        c.selection.selectionChanged(fromLine, toLine, newFromChar, newToChar)

        return true
    }

    companion object Key : CommandKey
}
