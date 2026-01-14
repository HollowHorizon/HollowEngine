package ru.hollowhorizon.hollowengine.client.gui.markdown

import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.MsdfFont
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.getTextInNode
import org.intellij.markdown.flavours.gfm.GFMElementTypes
import org.intellij.markdown.flavours.gfm.GFMTokenTypes
import ru.hollowhorizon.hollowengine.client.gui.colors.Dimensions
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.util.AttributedText
import ru.hollowhorizon.hollowengine.client.kool.minecraft.Image
import ru.hollowhorizon.hollowengine.common.scripting.ScriptingEnvironment

fun UiScope.MarkdownParagraph(
    node: ASTNode,
    source: String,
    style: MarkdownStyle,
) {
    val spans = collectSpans(node, source, TextAttributes(style.bodyFont, style.textColor), style)

    Column(Grow.Std) {
        modifier.padding(bottom = Dp(16f))

        val maxWidth = remember(0f)

        modifier.onMeasured {
            maxWidth.set(it.innerWidthPx)
        }

        val visualLines = wrapText(spans, maxWidth.use())

        visualLines.forEach { line ->
            AttributedText(line) {
                modifier.width(Grow.Std)
            }
        }
    }
}

fun UiScope.MarkdownHeader(
    node: ASTNode,
    source: String,
    font: MsdfFont,
    color: Color,
    maxWidth: Float,
) {
    val text = node.getTextInNode(source).toString().trim { it == '#' || it.isWhitespace() }
    val spans = listOf(text to TextAttributes(font, color))

    val visualLines = rememberTarget(node, maxWidth) {
        wrapText(spans, maxWidth)
    }

    Column(width = Grow.Std) {
        modifier.padding(top = Dp(16f), bottom = Dp(8f))
        visualLines.forEach { line ->
            AttributedText(line) {
                modifier.width(Grow.Std)
            }
        }
    }
}

fun UiScope.MarkdownCodeBlock(node: ASTNode, source: String, style: MarkdownStyle) {
    val text = node.children.filter { it.type == MarkdownTokenTypes.CODE_FENCE_CONTENT }
        .joinToString("\n") { it.getTextInNode(source) }
    val lines = ScriptingEnvironment.INSTANCE.analyzer.highlight("hightlight.kts", text, -1)
        .map { it.toKool(style.bodyFont) }
    Column(width = Grow.Std) {
        modifier.padding(Dimensions.PaddingMedium)
            .background(RoundRectBackground(style.codeBackgroundColor, Dimensions.PaddingMedium))

        lines.forEach { line ->
            AttributedText(line) {
                modifier.width(Grow.Std)
            }
        }
    }
}

fun UiScope.MarkdownImage(node: ASTNode, source: String, style: MarkdownStyle) {
    val text = node.getTextInNode(source).toString()
    val urlStart = text.indexOf('(') + 1
    val urlEnd = text.lastIndexOf(')')

    if (urlStart > 0 && urlEnd > urlStart) {
        val rawUrl = text.substring(urlStart, urlEnd)

        Image(rawUrl) {
            modifier
                .padding(vertical = Dp(8f))
                .width(Grow.Std)
        }

        // Опционально: отобразить подпись (alt text), если она есть
        val altTextStart = text.indexOf('[') + 1
        val altTextEnd = text.lastIndexOf(']')
        if (altTextEnd > altTextStart) {
            val alt = text.substring(altTextStart, altTextEnd)
            if (alt.isNotEmpty()) {
                Text(alt) {
                    modifier
                        .alignX(AlignmentX.Center)
                        .textColor(style.textColor.withAlpha(0.7f))
                        .font(style.italicFont)
                        .margin(top = Dp(4f))
                }
            }
        }
    }
}

fun UiScope.MarkdownTable(node: ASTNode, source: String, style: MarkdownStyle) {
    val alignCache = remember { mutableStateOf<List<AlignmentX>?>(null) }
    val prevNodeState = remember { mutableStateOf<ASTNode?>(null) }

    if (prevNodeState.value != node) {
        alignCache.set(parseTableAlignments(node, source))
        prevNodeState.set(node)
    }

    val alignments = alignCache.use() ?: emptyList()

    val headerNode = node.children.find { it.type == GFMElementTypes.HEADER }
    val rowNodes = node.children.filter { it.type == GFMElementTypes.ROW }

    val headerCells = headerNode?.children?.filter { it.type == GFMTokenTypes.CELL } ?: emptyList()
    val bodyRowsCells = rowNodes.map { row ->
        row.children.filter { it.type == GFMTokenTypes.CELL }
    }

    val maxColumns = (listOf(headerCells.size) + bodyRowsCells.map { it.size }).maxOrNull() ?: 0

    Box {
        modifier
            .background(RectBackground(Color.WHITE.withAlpha(0.05f)))
            .border(RectBorder(style.tableBorderColor, Dp(1f)))

        Row {
            for (colIndex in 0 until maxColumns) {

                if (colIndex > 0) {
                    Box(width = Dp(1f), height = Grow.Std) {
                        modifier.backgroundColor(style.tableBorderColor.withAlpha(0.5f))
                    }
                }

                Column {

                    val alignment = alignments.getOrNull(colIndex) ?: AlignmentX.Start

                    if (headerNode != null) {
                        val cellNode = headerCells.getOrNull(colIndex)
                        MarkdownTableCell(
                            cellNode = cellNode,
                            source = source,
                            style = style,
                            alignment = AlignmentX.Center,
                            isHeader = true,
                            bgColor = style.tableHeaderBgColor
                        )

                        Box(width = Grow.Std, height = Dp(1f)) {
                            modifier.backgroundColor(style.tableBorderColor)
                        }
                    }

                    bodyRowsCells.forEachIndexed { rowIndex, cells ->
                        val cellNode = cells.getOrNull(colIndex)
                        val rowColor = if (rowIndex % 2 == 0) style.tableEvenRowColor else style.tableOddRowColor

                        MarkdownTableCell(
                            cellNode = cellNode,
                            source = source,
                            style = style,
                            alignment = alignment,
                            isHeader = false,
                            bgColor = rowColor
                        )
                    }
                }
            }
        }
    }
}

private fun UiScope.MarkdownTableCell(
    cellNode: ASTNode?,
    source: String,
    style: MarkdownStyle,
    alignment: AlignmentX,
    isHeader: Boolean,
    bgColor: Color?,
) {
    Box {
        modifier
            .width(Grow.Std)
            .padding(horizontal = Dimensions.PaddingNormal, vertical = Dimensions.PaddingNormal)

        val width = remember(0f)

        modifier.onMeasured {
            width.set(it.innerWidthPx)
        }

        if (bgColor != null) {
            modifier.backgroundColor(bgColor)
        }

        if (cellNode != null) {
            val spans = collectSpans(
                cellNode,
                source,
                TextAttributes(if (isHeader) style.boldFont else style.bodyFont, style.textColor),
                style
            )

            AttributedText(TextLine(sanitize(spans))) {
                modifier
                    .width(Grow.Std)
                    .textAlignX(alignment)
            }
        }
    }
}

private fun parseTableAlignments(tableNode: ASTNode, source: String): List<AlignmentX> {
    val tableText = tableNode.getTextInNode(source).toString().trim()
    val lines = tableText.lines()
    if (lines.size < 2) return emptyList()

    val separatorLine = lines[1].trim()
    if (!separatorLine.startsWith("|")) return emptyList()

    val cols = separatorLine.split('|').filter { it.isNotBlank() }
    val alignments = mutableListOf<AlignmentX>()

    for (col in cols) {
        val trimmed = col.trim()
        val align = when {
            trimmed.startsWith(":") && trimmed.endsWith(":") -> AlignmentX.Center
            trimmed.endsWith(":") -> AlignmentX.End
            else -> AlignmentX.Start
        }
        alignments.add(align)
    }
    return alignments
}

private fun wrapText(spans: List<Pair<String, TextAttributes>>, maxWidth: Float): List<TextLine> {
    val visualLines = mutableListOf<TextLine>()
    var currentLineSpans = mutableListOf<Pair<String, TextAttributes>>()
    var currentLineWidth = 0f

    val effectiveWidth = maxWidth - 1f

    for ((text, attrs) in spans) {
        val parts = text.split(Regex("\\s+"))

        for (i in parts.indices) {
            val word = parts[i]
            if (word.isEmpty()) continue

            val wordWidth = measureStringWidth(word + " ", attrs.font)

            if (currentLineWidth + wordWidth > effectiveWidth && currentLineWidth > 0) {
                visualLines.add(TextLine(sanitize(currentLineSpans)))
                currentLineSpans = mutableListOf()
                currentLineWidth = 0f
            }

            currentLineSpans.add(word to attrs)
            currentLineWidth += wordWidth

            if (i < parts.size - 1) {
                val space = " "
                val spaceWidth = measureStringWidth(space, attrs.font)

                currentLineSpans.add(space to attrs)
                currentLineWidth += spaceWidth
            }
        }

        if (text.endsWith(" ") || text.endsWith("\n")) {
            val space = " "
            val spaceWidth = measureStringWidth(space, attrs.font)
            currentLineSpans.add(space to attrs)
            currentLineWidth += spaceWidth
        }
    }

    if (currentLineSpans.isNotEmpty()) {
        visualLines.add(TextLine(sanitize(currentLineSpans)))
    }

    return visualLines
}

private fun measureStringWidth(str: String, font: MsdfFont): Float {
    var w = 0f
    for (c in str) {
        w += font.charWidth(c)
    }
    return w
}

/**
 * Исправленная функция сбора спанов.
 * Реализован рекурсивный обход дерева (Visitor), что позволяет:
 * 1. Корректно обрабатывать вложенность (напр. **Жирный _курсив_**).
 * 2. Игнорировать ошибки разметки (незакрытые теги обрабатываются как текст).
 * 3. Поддерживать кастомную логику цветов в ссылках.
 */
private fun collectSpans(
    node: ASTNode,
    source: String,
    parentAttr: TextAttributes,
    style: MarkdownStyle,
): List<Pair<String, TextAttributes>> {
    val result = mutableListOf<Pair<String, TextAttributes>>()

    fun visit(currentNode: ASTNode, currentAttr: TextAttributes) {
        // Если у ноды есть дети, рекурсивно обходим их, модифицируя атрибуты
        // Если детей нет (Leaf node), выводим текст

        // Специфическая обработка типов нод
        when (currentNode.type) {
            // --- Контейнеры (меняют стиль для детей) ---
            MarkdownElementTypes.STRONG -> {
                // Вложенный обход с жирным шрифтом
                currentNode.children.forEach {
                    visit(
                        it,
                        currentAttr.copy(font = currentAttr.font.copy(weight = MsdfFont.WEIGHT_EXTRA_BOLD))
                    )
                }
            }

            MarkdownElementTypes.EMPH -> {
                // Вложенный обход с курсивом
                currentNode.children.forEach {
                    visit(
                        it,
                        currentAttr.copy(font = currentAttr.font.copy(italic = MsdfFont.ITALIC_STD))
                    )
                }
            }

            MarkdownElementTypes.INLINE_LINK -> {
                // Логика ссылок и цветов
                val destinationNode = currentNode.children.find { it.type == MarkdownElementTypes.LINK_DESTINATION }
                val textNode = currentNode.children.find { it.type == MarkdownElementTypes.LINK_TEXT }

                val destination = destinationNode?.getTextInNode(source)?.toString() ?: ""

                val linkAttr = if (destination.startsWith("color:")) {
                    val colorName = destination.removePrefix("color:")
                    val color = parseColorString(colorName) ?: currentAttr.color
                    currentAttr.copy(color = color)
                } else {
                    currentAttr.copy(color = style.linkColor)
                }

                // Обрабатываем содержимое текста ссылки (там тоже может быть форматирование)
                // LINK_TEXT обычно содержит [ content ], нам нужен content
                textNode?.children?.forEach { child ->
                    if (child.type != MarkdownTokenTypes.LBRACKET && child.type != MarkdownTokenTypes.RBRACKET) {
                        visit(child, linkAttr)
                    }
                }
            }

            MarkdownElementTypes.CODE_SPAN -> {
                // Код внутри строки. Текст внутри берется буквально, но со своим шрифтом
                val codeAttr = currentAttr.copy(font = style.codeFont, background = style.codeBackgroundColor)
                currentNode.children.forEach { child ->
                    // Пропускаем сами тики (`), если они есть как отдельные токены, но обычно текст внутри
                    if (child.type == MarkdownTokenTypes.TEXT || child.type == MarkdownTokenTypes.CODE_FENCE_CONTENT) {
                        // В коде не анэскейпим символы
                        result.add(child.getTextInNode(source).toString() to codeAttr)
                    }
                }
            }

            // --- Листовые ноды (Текст) ---
            MarkdownTokenTypes.TEXT,
            MarkdownTokenTypes.WHITE_SPACE,
            MarkdownTokenTypes.COLON,
            MarkdownTokenTypes.LPAREN,
            MarkdownTokenTypes.RPAREN,
            MarkdownTokenTypes.LBRACKET,
            MarkdownTokenTypes.RBRACKET,
                -> {
                // Обычный текст: нужно обработать экранирование
                val text = currentNode.getTextInNode(source).toString()
                // Исправляем экранирование: заменяем `\*` на `*` и т.д.
                // Простейший вариант - убрать слэш, если за ним идет символ.
                val unescaped = unescapeMarkdown(text)
                result.add(unescaped to currentAttr)
            }

            MarkdownTokenTypes.ESCAPED_BACKTICKS -> {
                result.add("`" to currentAttr)
            }

            // Если мы попали сюда с контейнером, который не обработали выше (например, PARAGRAPH при первом вызове),
            // просто идем вглубь
            else -> {
                if (currentNode.children.isNotEmpty()) {
                    currentNode.children.forEach { visit(it, currentAttr) }
                } else {
                    // Fallback для неизвестных токенов - просто печатаем их текст
                    // Но лучше игнорировать служебные символы разметки, если они не обработаны выше
                }
            }
        }
    }

    visit(node, parentAttr)
    return result
}

// Функция для снятия экранирования (например, "foo\*bar" -> "foo*bar")
private fun unescapeMarkdown(text: String): String {
    if (!text.contains('\\')) return text
    val sb = StringBuilder(text.length)
    var escaped = false
    for (c in text) {
        if (escaped) {
            sb.append(c)
            escaped = false
        } else if (c == '\\') {
            escaped = true
        } else {
            sb.append(c)
        }
    }
    // Если строка заканчивается на \, добавляем его (хотя в markdown это редкость)
    if (escaped) sb.append('\\')
    return sb.toString()
}

private fun sanitize(spans: List<Pair<String, TextAttributes>>): List<Pair<String, TextAttributes>> {
    val newSpans = mutableListOf<Pair<String, TextAttributes>>()
    if (spans.isNotEmpty()) {
        var prevSpan = spans[0]
        newSpans += prevSpan
        for (i in 1 until spans.size) {
            val span = spans[i]
            if (span.second == prevSpan.second) {
                // Если атрибуты совпадают, объединяем строки
                prevSpan = prevSpan.first + span.first to prevSpan.second
                newSpans[newSpans.lastIndex] = prevSpan
            } else if (span.first.isNotEmpty()) {
                prevSpan = span
                newSpans += span
            }
        }
    }
    return newSpans
}

private fun parseColorString(colorStr: String): Color? {
    return try {
        when (val c = colorStr.lowercase()) {
            "dark_blue" -> Color("0000AA")
            "dark_green" -> Color("00AA00")
            "dark_aqua" -> Color("00AAAA")
            "dark_red" -> Color("AA0000")
            "dark_purple" -> Color("AA00AA")
            "gold" -> Color("FFAA00")
            "gray" -> Color("AAAAAA")
            "dark_gray" -> Color("555555")
            "blue" -> Color("5555FF")
            "green" -> Color("55FF55")
            "aqua" -> Color("55FFFF")
            "red" -> Color("FF5555")
            "light_purple" -> Color("FF55FF")
            "yellow" -> Color("FFFF55")
            "white" -> Color("FFFFFF")
            else -> Color(c)
        }
    } catch (e: Exception) {
        null
    }
}