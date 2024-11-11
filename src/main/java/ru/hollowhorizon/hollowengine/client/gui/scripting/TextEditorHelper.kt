package ru.hollowhorizon.hollowengine.client.gui.scripting

import imgui.extension.texteditor.TextEditor

fun TextEditor.insertAtCursor(text: String) {
    var currentLine = currentLineText

    currentLine = if (!hasSelection()) {
        if (cursorPosition.mColumn == 0) text + currentLine
        else currentLine.substring(0, cursorPosition.mColumn) + text + currentLine.substring(cursorPosition.mColumn)
    } else {
        currentLine.replace(selectedText, text)
    }

    val lines = textLines
    lines[cursorPosition.mLine] = currentLine
    textLines = lines
}