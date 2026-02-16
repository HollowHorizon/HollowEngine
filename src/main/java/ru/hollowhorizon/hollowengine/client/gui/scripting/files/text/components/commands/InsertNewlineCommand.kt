package ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.components.commands

import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.components.Command
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.components.CommandKey
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.components.CommandRegistry
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.components.EditorCommandContext
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.util.TextAreaConfig


class InsertNewlineCommand : Command {
    override fun execute(c: EditorCommandContext): Boolean {
        if (c.hasCompletions) {
            CommandRegistry.execute(ApplyCompletionItemCommand.Key, c)
            return true
        }

        val line = c.selection.caretLine ?: return false
        val text = line.text
        val caretPos = c.selection.selectionCaretChar.coerceAtMost(text.length)

        var whitespaces = text.takeWhile { it == ' ' }.length

        val isLPar = text.substring(0, caretPos).trimEnd().endsWith("{")
        val isRPar = text.substring(caretPos).trimStart().startsWith("}")

        if (isLPar) whitespaces += TextAreaConfig.INDENT_SIZE

        if (isLPar && isRPar) {
            val baseIndent = (whitespaces - TextAreaConfig.INDENT_SIZE).coerceAtLeast(0)
            val indentStr = " ".repeat(whitespaces)
            val closeIndentStr = " ".repeat(baseIndent)

            c.inputController.editText("\n$indentStr\n$closeIndentStr")
            c.selection.moveCaretLineUp(select = false)
            c.selection.moveCaretLineEnd(select = false)
        } else {
            c.inputController.editText("\n" + " ".repeat(whitespaces))
        }

        return true
    }

    companion object Key : CommandKey
}
