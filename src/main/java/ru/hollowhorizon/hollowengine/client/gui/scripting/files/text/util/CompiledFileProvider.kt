package ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.util

import de.fabmax.kool.math.Vec2i
import de.fabmax.kool.modules.ui2.TextAttributes
import de.fabmax.kool.modules.ui2.TextLine
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.MsdfFont
import ru.hollowhorizon.hollowengine.client.gui.scripting.HACK_FONT
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.UndoRedoHandler
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.UndoableAction
import ru.hollowhorizon.hollowengine.common.ide.highlight.highlightCode
import java.io.File
import java.util.*

class CompiledFileProvider(
    val file: File,
) : TextLineProvider, TextEditorHandler, UndoRedoHandler {
    val font = MsdfFont(HACK_FONT, 18f)
    val lines = ArrayList<TextLine>().apply {
        file.useLines { lines ->
            lines.forEach { line ->
                add(TextLine(listOf(line to TextAttributes(font, Color.WHITE))))
            }
        }
    }

    override val size get() = lines.size

    private val undoStack: Stack<UndoableAction> = Stack()
    private val redoStack: Stack<UndoableAction> = Stack()

    private var currentText: String = ""

    override fun get(index: Int): TextLine {
        if (index < 0 || index >= size) {
            throw IndexOutOfBoundsException("Index $index is out of bounds for CompiledFileProvider with size $size.")
        }
        return lines[index]
    }

    override fun insertText(line: Int, caret: Int, insertion: String): Vec2i {
        return replaceText(line, line, caret, caret, insertion)
    }

    override fun replaceText(
        selectionStartLine: Int,
        selectionEndLine: Int,
        selectionStartChar: Int,
        selectionEndChar: Int,
        replacement: String,
    ): Vec2i {
        val oldLinesList = mutableListOf<TextLine>()
        for (i in selectionStartLine..selectionEndLine) {
            oldLinesList.add(lines[i])
        }


        // 2. Обновление текста (модификация lines)

        // A. Формируем новую строку для замены
        val lineBefore = lines[selectionStartLine].text.substring(0, selectionStartChar)
        val lineAfter = lines[selectionEndLine].text.substring(selectionEndChar)
        val newTextFull = lineBefore + replacement + lineAfter
        val newLinesRaw = newTextFull.split('\n')
        val offset = lines.subList(0, (selectionStartLine-1).coerceAtLeast(0)).sumOf(TextLine::length) + selectionStartChar

        // B. Удаляем старые линии
        // Количество линий для удаления: selectionEndLine - selectionStartLine + 1
        val numLinesToRemove = selectionEndLine - selectionStartLine + 1
        lines.subList(selectionStartLine, selectionStartLine + numLinesToRemove).clear()

        // C. Добавляем новые линии
        // Выполняем синтаксическую подсветку (highlightCode) для новых строк
        val newText = newLinesRaw.joinToString("\n")
        val highlightedNewLines = newText.lines().map { TextLine(listOf(it to TextAttributes(font, Color.WHITE))) }

        // Теперь у нас есть список TextLine, их нужно вставить
        lines.addAll(selectionStartLine, highlightedNewLines)

        // 3. Обновление currentText для синхронизации
        currentText = lines.joinToString("\n") { it.text }
        lines.clear()
        lines.addAll(highlightCode(font, file.name, currentText, offset))

        // 4. Расчет новой позиции каретки (caret)
        val newCaretLine = selectionStartLine + newLinesRaw.lastIndex
        val newCaretChar = newLinesRaw.last().length - lineAfter.length

        // 5. Создание и сохранение UndoableAction
        val action = UndoableAction(
            startLine = selectionStartLine,
            caretLine = newCaretLine,
            startChar = selectionStartChar,
            caretChar = newCaretChar,
            numOldLines = numLinesToRemove, // Количество удаленных строк
            oldLines = oldLinesList, // Старые TextLine
            newLines = highlightedNewLines, // Новые TextLine
        )

        undoStack.push(action)
        redoStack.clear() // При новом действии история redo сбрасывается

        // 6. Возвращаем новую позицию каретки
        return Vec2i(newCaretChar, newCaretLine)
    }

    fun setText(text: String) {
        // Очищаем историю при полной установке текста
        undoStack.clear()
        redoStack.clear()

        // Заменяем весь текст. Определяем границы для всего файла.
        val startLine = 0
        val endLine = lines.lastIndex.coerceAtLeast(0) // Учитываем пустой файл
        val startChar = 0
        val endChar = lines.lastOrNull()?.text?.length ?: 0

        // Используем replaceText для корректной обработки (сохранится в undo/redo как одно действие)
        replaceText(startLine, endLine, startChar, endChar, text)
    }

    private fun applyAction(action: UndoableAction, isUndo: Boolean, onSelectionChanged: ((Int, Int, Int, Int) -> Unit)?) {
        // Определяем, что заменяем (удаляем) и что вставляем (возвращаем)
        val linesToRemove: List<TextLine>
        val linesToInsert: List<TextLine>
        val finalCaretLine: Int
        val finalCaretChar: Int

        if (isUndo) {
            // При отмене: удаляем newLines, вставляем oldLines.
            linesToRemove = action.newLines
            linesToInsert = action.oldLines
            // Позиция каретки после отмены - начальная позиция действия.
            finalCaretLine = action.startLine
            finalCaretChar = action.startChar
        } else {
            // При повторе: удаляем oldLines, вставляем newLines.
            linesToRemove = action.oldLines
            linesToInsert = action.newLines
            // Позиция каретки после повтора - позиция после выполнения действия.
            finalCaretLine = action.caretLine
            finalCaretChar = action.caretChar
        }

        // A. Удаление
        // Количество линий для удаления
        val numLinesToRemove = linesToRemove.size
        // Начальная позиция для удаления/вставки
        val startLineIndex = action.startLine

        // Проверяем границы, чтобы избежать IndexOutOfBoundsException
        if (startLineIndex >= 0 && startLineIndex + numLinesToRemove <= lines.size) {
            lines.subList(startLineIndex, startLineIndex + numLinesToRemove).clear()
        } else {
            // Обработка случая, когда, например, файл пуст
            lines.clear()
        }

        // B. Вставка
        lines.addAll(startLineIndex, linesToInsert)

        // C. Обновление currentText
        currentText = lines.joinToString("\n") { it.text }

        // D. Уведомление об изменении позиции каретки
        onSelectionChanged?.invoke(finalCaretLine, finalCaretChar, finalCaretLine, finalCaretChar)
    }

    override fun undo(onSelectionChanged: ((Int, Int, Int, Int) -> Unit)?) {
        if (undoStack.isNotEmpty()) {
            val action = undoStack.pop()
            applyAction(action, isUndo = true, onSelectionChanged)
            redoStack.push(action)
        }
    }

    override fun redo(onSelectionChanged: ((Int, Int, Int, Int) -> Unit)?) {
        if (redoStack.isNotEmpty()) {
            val action = redoStack.pop()
            applyAction(action, isUndo = false, onSelectionChanged)
            undoStack.push(action)
        }
    }
}