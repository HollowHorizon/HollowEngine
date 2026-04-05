package ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.components.commands

import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.components.Command
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.components.CommandKey
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.components.EditorCommandContext

class ToggleLineCommentCommand : Command {
    override fun execute(c: EditorCommandContext): Boolean {
        val startLineBefore = c.selection.selectionStartLine
        val caretLineBefore = c.selection.selectionCaretLine
        val startCharBefore = c.selection.selectionStartChar
        val caretCharBefore = c.selection.selectionCaretChar

        val fromLine = c.selection.selectionFromLine
        val toLine = c.selection.selectionToLine

        if (c.lineProvider.size == 0) return false
        if (fromLine !in 0 until c.lineProvider.size) return false

        val deltas = HashMap<Int, Int>()

        for (line in fromLine..toLine.coerceAtMost(c.lineProvider.lastIndex)) {
            val text = c.lineProvider[line].text
            val indentSize = text.indexOfFirst { it != ' ' }.let { if (it == -1) 0 else it }
            val afterIndent = text.drop(indentSize)

            if (afterIndent.startsWith("//")) {
                val removeFrom = indentSize
                var removeTo = (indentSize + 2).coerceAtMost(text.length)
                if (text.getOrNull(removeTo) == ' ') removeTo += 1

                c.inputController.replaceText(line, line, removeFrom, removeTo, "")
                deltas[line] = deltas.getOrDefault(line, 0) - (removeTo - removeFrom)
            } else {
                c.inputController.insertText(line, indentSize, "//")
                deltas[line] = deltas.getOrDefault(line, 0) + 2
            }
        }

        fun adjustChar(line: Int, char: Int): Int {
            val delta = deltas[line] ?: 0
            return (char + delta).coerceAtLeast(0)
        }

        val newStartChar = adjustChar(startLineBefore, startCharBefore)
        val newCaretChar = adjustChar(caretLineBefore, caretCharBefore)

        c.selection.selectionChanged(
            startLineBefore,
            caretLineBefore,
            newStartChar,
            newCaretChar,
            false
        )

        return true
    }

    companion object Key : CommandKey
}
