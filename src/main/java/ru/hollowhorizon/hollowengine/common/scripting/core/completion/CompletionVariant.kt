package ru.hollowhorizon.hollowengine.common.scripting.core.completion

import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.TextCaretNavigation
import org.eclipse.lsp4j.CompletionItemKind
import org.eclipse.lsp4j.Position
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import ru.hollowhorizon.hc.client.kool.minecraft.Image
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.fullText
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.util.CompiledFileProvider
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.util.TextAreaNode
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.util.setSelectionRange
import ru.hollowhorizon.hollowengine.client.gui.scripting.tools.hoverColors
import ru.hollowhorizon.hollowengine.common.project.kt.position.position

data class CompletionVariant(
    val insertText: String,
    val displayText: String,
    val tail: String,
    val icon: Icon,
    val matchIndices: List<Int> = emptyList(), // Индексы совпадающих символов для подсветки
    val textEdits: List<Pair<Position, String>> = emptyList(),
    val isLambda: Boolean,
) {
    override fun toString() = displayText

    fun UiScope.create(textArea: TextAreaNode, isFocused: Boolean) {
        Row(Grow.Std) {
            val (bgColor, iconBg, iconTint) = hoverColors(
                0.5f,
                listOf(Color("1B1E23FF"), Color("394450FF"), Color("6DA1E2FF")),
                listOf(Color("252930FF"), Color("586D84FF"), Color("98C6FFFF"))
            )

            if (isFocused) {
                bgColor.set(Color("252930FF"))
                iconBg.set(Color("586D84FF"))
                iconTint.set(Color("98C6FFFF"))
            }

            modifier.onClick { use(textArea) }
            modifier.background(RectBackground(bgColor))

            when (icon) {
                Icon.UNKNOWN -> Box {
                    modifier.alignY(AlignmentY.Center)
                        .size(24.dp + sizes.smallGap, 24.dp + sizes.smallGap).padding(sizes.smallGap)
                        .background(RectBackground(iconBg))
                }

                else -> Image("hollowengine:textures/gui/icons/autocomplete_${icon.name.lowercase()}.svg") {
                    modifier.alignY(AlignmentY.Center)
                        .size(24.dp + sizes.smallGap, 24.dp + sizes.smallGap).padding(sizes.smallGap)
                        .background(RectBackground(iconBg))
                        .tint(iconTint)
                }
            }

            // Текст с подсветкой совпадений
            Row {
                modifier.align(AlignmentX.Start, AlignmentY.Center).margin(horizontal = sizes.smallGap)

                // Разбиваем текст на части для подсветки
                val parts = splitTextWithHighlights(displayText, matchIndices)
                parts.forEach { (textPart, isHighlighted) ->
                    Text(textPart) {
                        if (isHighlighted) {
                            modifier.textColor(Color("98C6FFFF")) // Синий цвет подсветки
                        }
                    }
                }
            }

            Box {
                modifier.width(Grow.Std).alignY(AlignmentY.Center)

                Text(tail) {
                    modifier.align(AlignmentX.End, AlignmentY.Center)
                        .margin(horizontal = sizes.smallGap)
                        .textColor(Color.LIGHT_GRAY)
                }
            }
        }
    }

    fun use(scriptTextArea: TextAreaNode) {
        val modifier = scriptTextArea.modifier
        val provider = scriptTextArea.lineProvider as CompiledFileProvider
        var lineIndex = modifier.selectionStartLine.coerceAtMost(scriptTextArea.lineProvider.lastIndex)
        if (lineIndex == -1) return

        // Добавление импортов
        if (textEdits.isNotEmpty()) {
            textEdits.forEach { (position, text) ->
                lineIndex += provider.insertText(position.line, position.character, text).y - position.line
            }
        }


        val line = scriptTextArea.lineProvider[lineIndex].text
        val startChar = (modifier.selectionStartChar - 1).coerceAtLeast(0)
        val startWord = TextCaretNavigation.startOfWord(line, startChar)

        val replacement = insertText + if (isLambda) " {  }" else if (icon == Icon.METHOD) "()" else ""

        provider.replaceText(
            lineIndex,
            lineIndex,
            startWord,
            startChar + 1,
            replacement,
        )

        // Установка курсора в правильное место
        val cursorPos = startWord + replacement.length - if (isLambda) 2 else if (icon == Icon.METHOD) 1 else 0
        modifier.setSelectionRange(
            lineIndex,
            lineIndex,
            cursorPos,
            cursorPos
        )

        modifier.completions.clear()
        modifier.setCompletionIndex(0)
    }

    // Вспомогательная функция для разбивки текста на подсвеченные части
    private fun splitTextWithHighlights(text: String, indices: List<Int>): List<Pair<String, Boolean>> {
        if (indices.isEmpty()) return listOf(text to false)

        val parts = mutableListOf<Pair<String, Boolean>>()
        var lastIndex = 0
        var currentGroupStart = indices.first()
        var currentGroupEnd = currentGroupStart

        // Группируем смежные индексы
        for (i in 1 until indices.size) {
            if (indices[i] == currentGroupEnd + 1) {
                currentGroupEnd = indices[i]
            } else {
                // Добавляем текст до группы
                if (currentGroupStart > lastIndex) {
                    parts.add(text.substring(lastIndex, currentGroupStart) to false)
                }

                // Добавляем подсвеченную группу
                parts.add(text.substring(currentGroupStart, currentGroupEnd + 1) to true)

                lastIndex = currentGroupEnd + 1
                currentGroupStart = indices[i]
                currentGroupEnd = currentGroupStart
            }
        }

        // Обработка последней группы
        if (currentGroupStart > lastIndex) {
            parts.add(text.substring(lastIndex, currentGroupStart) to false)
        }
        parts.add(text.substring(currentGroupStart, currentGroupEnd + 1) to true)

        // Добавляем оставшийся текст
        if (currentGroupEnd + 1 < text.length) {
            parts.add(text.substring(currentGroupEnd + 1) to false)
        }

        return parts
    }

    enum class Icon {
        PACKAGE, CLASS, METHOD, VARIABLE, UNKNOWN;

        companion object {
            fun fromKind(kind: CompletionItemKind) = when (kind) {
                CompletionItemKind.File, CompletionItemKind.Folder -> PACKAGE
                CompletionItemKind.Class, CompletionItemKind.Enum, CompletionItemKind.Interface -> CLASS
                CompletionItemKind.Method, CompletionItemKind.Function, CompletionItemKind.Operator, CompletionItemKind.Constructor -> METHOD
                CompletionItemKind.Field, CompletionItemKind.Variable, CompletionItemKind.Property, CompletionItemKind.Value -> VARIABLE
                else -> UNKNOWN
            }
        }
    }
}