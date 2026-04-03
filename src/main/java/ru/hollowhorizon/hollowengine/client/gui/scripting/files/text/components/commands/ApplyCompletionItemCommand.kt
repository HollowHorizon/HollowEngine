package ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.components.commands

import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.components.Command
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.components.CommandKey
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.components.EditorCommandContext
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.util.TextCaretNavigation
import ru.hollowhorizon.hollowengine.common.scripting.ide.CompletionItem

class ApplyCompletionItemCommand : Command {
    override fun canExecute(c: EditorCommandContext): Boolean {
        return c.hasCompletions
    }

    override fun execute(c: EditorCommandContext): Boolean {
        val item = c.completionItem ?: return false
        val handler = c.inputController.modifier.editorHandler ?: return false
        val provider = handler as? ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.util.CompiledFileProvider
        var lineIdx = c.selection.selectionCaretLine
        val charIdx = c.selection.selectionCaretChar

        if (item is CompletionItem.Declaration && item.import && !item.fqName.isNullOrBlank()) {
            val linesAdded = ensureImport(item.fqName, handler, c.lineProvider)
            lineIdx += linesAdded
        }

        val lineText = c.lineProvider[lineIdx].text

        val replaceStart = TextCaretNavigation.startOfIdentifier(lineText, charIdx)

        val newPos = handler.replaceText(lineIdx, lineIdx, replaceStart, charIdx, item.insert)

        if (item.moveCaret != 0) {
            val customCaretX = newPos.x + item.moveCaret
            c.selection.selectionChanged(newPos.y, newPos.y, customCaretX, customCaretX)
        } else {
            c.selection.selectionChanged(newPos.y, newPos.y, newPos.x, newPos.x)
        }

        provider?.analysisState?.completions?.clear()
        c.inputController.modifier.completions.clear()

        return true
    }

    private fun ensureImport(
        fqName: String,
        handler: ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.util.TextEditorHandler,
        lineProvider: ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.util.TextLineProvider
    ): Int {
        val importLine = "import $fqName"

        val textLines = (0 until lineProvider.size).map { lineProvider[it].text }

        if (textLines.any { it.trim() == importLine }) return 0

        var insertIndex = 0
        var foundPackage = false
        var lastImportIndex = -1

        for ((i, line) in textLines.withIndex()) {
            val trimmed = line.trim()
            if (trimmed.startsWith("package ")) {
                foundPackage = true
                insertIndex = i + 1
            } else if (trimmed.startsWith("import ")) {
                lastImportIndex = i
                if (importLine < trimmed) {
                    insertIndex = i
                    break
                } else {
                    insertIndex = i + 1
                }
            } else if (trimmed.isNotBlank() && lastImportIndex != -1) {
                break
            }
        }

        val textToInsert = if (insertIndex == 0 && !foundPackage) "$importLine\n" else "$importLine\n"
        handler.insertText(insertIndex, 0, textToInsert)

        return 1
    }

    companion object Key : CommandKey
}
