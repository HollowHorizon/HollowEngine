package ru.hollowhorizon.hollowengine.client.gui.scripting.files.text

import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.util.ScriptTextAreaModifier
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.util.TextLineProvider

fun ScriptTextAreaModifier.getCharBeforeSelection(): Char? {
    val handler = editorHandler as? ScriptTextEditorHandler ?: return null
    val textProvider = handler.text
    if (textProvider.size == 0) return null

    // Получаем упорядоченные границы выделения
    val (start, _) = getOrderedSelection() ?: return null
    val (startLine, startChar) = start

    // Получаем текст строки, где начинается выделение
    val line = textProvider[startLine].text

    return when {
        // Символ слева в той же строке
        startChar > 0 -> line.getOrNull(startChar - 1)
        // Первый символ предыдущей строки
        startLine > 0 -> textProvider[startLine - 1].text.lastOrNull()
        // Нет доступных символов
        else -> null
    }
}

fun ScriptTextAreaModifier.getCharAfterSelection(): Char? {
    val handler = editorHandler as? ScriptTextEditorHandler ?: return null
    val textProvider = handler.text
    if (textProvider.size == 0) return null

    // Получаем упорядоченные границы выделения
    val (_, end) = getOrderedSelection() ?: return null
    val (endLine, endChar) = end

    // Получаем текст строки, где заканчивается выделение
    val line = textProvider[endLine].text

    return when {
        // Символ справа в той же строке
        endChar < line.length -> line.getOrNull(endChar)
        // Первый символ следующей строки
        endLine < textProvider.lastIndex -> textProvider[endLine + 1].text.firstOrNull()
        // Нет доступных символов
        else -> null
    }
}

private fun ScriptTextAreaModifier.getOrderedSelection(): Pair<Pair<Int, Int>, Pair<Int, Int>>? {
    val textProvider = (editorHandler as? ScriptTextEditorHandler)?.text ?: return null

    // Проверка валидности позиций
    if (selectionStartLine !in 0..textProvider.lastIndex ||
        selectionCaretLine !in 0..textProvider.lastIndex
    ) return null

    val startLine = selectionStartLine
    val startChar = selectionStartChar.coerceIn(0, textProvider[startLine].length)
    val caretLine = selectionCaretLine
    val caretChar = selectionCaretChar.coerceIn(0, textProvider[caretLine].length)

    // Определение начала и конца выделения
    return when {
        startLine < caretLine -> Pair(startLine to startChar, caretLine to caretChar)
        startLine > caretLine -> Pair(caretLine to caretChar, startLine to startChar)
        else -> {
            if (startChar <= caretChar) {
                Pair(startLine to startChar, caretLine to caretChar)
            } else {
                Pair(caretLine to caretChar, startLine to startChar)
            }
        }
    }
}

fun TextLineProvider.fullText(): String {
    return (0 until size).joinToString("\n") { index ->
        this[index].text
    }
}