package ru.hollowhorizon.hollowengine.common.scripting.core.completion

import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.TextCaretNavigation
import org.eclipse.lsp4j.CompletionItemKind
import org.jetbrains.kotlin.builtins.isFunctionOrSuspendFunctionType
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import ru.hollowhorizon.hc.client.kool.minecraft.Image
import ru.hollowhorizon.hc.common.events.Event
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.util.TextAreaNode
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.util.setSelectionRange
import ru.hollowhorizon.hollowengine.client.gui.scripting.tools.hoverColors

data class CompletionVariant(
    val text: String,
    val displayText: String,
    val tail: String,
    val icon: Icon,
    val descriptor: DeclarationDescriptor?,
    val matchIndices: List<Int> = emptyList(), // Индексы совпадающих символов для подсветки
    val additionalImports: List<String> = emptyList(),
) {
    override fun toString() = displayText
    val isLambda = (descriptor as? FunctionDescriptor)?.valueParameters?.lastOrNull()
        ?.type?.isFunctionOrSuspendFunctionType == true

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
                        .size(24.dp+sizes.smallGap, 24.dp+sizes.smallGap).padding(sizes.smallGap)
                        .background(RectBackground(iconBg))
                }
                else -> Image("hollowengine:textures/gui/icons/autocomplete_${icon.name.lowercase()}.svg") {
                    modifier.alignY(AlignmentY.Center)
                        .size(24.dp+sizes.smallGap, 24.dp+sizes.smallGap).padding(sizes.smallGap)
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
        var lineIndex = modifier.selectionStartLine.coerceAtMost(scriptTextArea.lineProvider.lastIndex)
        if (lineIndex == -1) return

        val line = scriptTextArea.lineProvider[lineIndex].text
        val startChar = (modifier.selectionStartChar - 1).coerceAtLeast(0)
        val startWord = TextCaretNavigation.startOfWord(line, startChar)
        val editor = modifier.editorHandler ?: return

        // Добавление импортов
        if (additionalImports.isNotEmpty()) {
            additionalImports.forEach {
                editor.insertText(0, 0, "import ${it}\n")
                lineIndex++
            }
        }

        var textToInsert = text
        var offset = 0

        // Упрощение для лямбд: добавляем шаблон
        if (isLambda) {
            val params = (descriptor as? FunctionDescriptor)?.valueParameters?.size ?: 1

            textToInsert = when {
                params == 1 -> {
                    offset = 2
                    "${textToInsert.dropLast(1)} {  }"
                }
                else -> {
                    offset = 4
                    "$textToInsert) {}"
                }
            }
        }
        // Добавление скобок для функций
        else if (textToInsert.endsWith("(")) {
            textToInsert = "$textToInsert)"
            offset = 1
        }

        editor.replaceText(
            lineIndex,
            lineIndex,
            startWord,
            startChar + 1,
            textToInsert,
        )

        // Установка курсора в правильное место
        val cursorPos = startWord + textToInsert.length - offset
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

class OnCompletionsEvent(val fileName: String, val completions: List<CompletionVariant>, val textVersion: Int) : Event