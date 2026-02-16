package ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.components.commands

import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.components.Command
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.components.CommandKey
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.components.EditorCommandContext


class ApplyBracketsCommand : Command {
    override fun execute(c: EditorCommandContext): Boolean {
        val char = c.bracketChar ?: return false
        val closing = c.bracketClosing ?: return false

        if (!c.selection.isEmptySelection) {
            val fromLine = c.selection.selectionFromLine
            val toLine = c.selection.selectionToLine
            val fromChar = c.selection.selectionFromChar
            val toChar = c.selection.selectionToChar

            val selectedText = c.selection.copySelection() ?: return false
            val editor = c.inputController.modifier.editorHandler ?: return false

            // Сохраняем позицию каретки после замены текста
            val newPos = editor.replaceText(
                fromLine, toLine, fromChar, toChar, "$char$selectedText$closing"
            )

            // Обновляем выделение на обёрнутый текст, используя позицию из replaceText
            c.selection.selectionChanged(
                newPos.y, newPos.y, newPos.x, newPos.x + selectedText.length
            )
        } else {
            c.inputController.editText(char)
            c.inputController.editText(closing.toString())
            c.selection.moveCaretLeft(wordWise = false, select = false)
        }

        return true
    }

    companion object Key : CommandKey
}
