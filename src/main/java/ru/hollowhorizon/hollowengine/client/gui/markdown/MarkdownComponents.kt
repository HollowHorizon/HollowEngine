package ru.hollowhorizon.hollowengine.client.gui.markdown

import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.MsdfFont
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.getTextInNode
import org.intellij.markdown.flavours.gfm.GFMElementTypes
import org.intellij.markdown.flavours.gfm.GFMTokenTypes
import kotlin.math.max

fun UiScope.MarkdownParagraph(
    node: ASTNode,
    source: String,
    style: MarkdownStyle,
) {
    val spans = collectSpans(node, source, TextAttributes(style.bodyFont, style.textColor), style)

    Column(width = Grow.Std) {
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
            // TODO: Лучше тут перейти на другудю систему, чтобы вставлять ссылки, выражения через гравис (`),
            //  и т.п. как разные независимые виджеты, чтобы лучше их анимировать.
            AttributedText(line) {
                modifier.width(Grow.Std)
            }
        }
    }
}

fun UiScope.MarkdownCodeBlock(node: ASTNode, source: String, style: MarkdownStyle) {
    val text = node.children.let {
        it.subList(it.indexOfFirst { it.type == MarkdownTokenTypes.CODE_FENCE_CONTENT }.takeIf { it >= 0 } ?: return,
            it.indexOfFirst { it.type == MarkdownTokenTypes.CODE_FENCE_END }.takeIf { it >= 0 } ?: return)
    }.joinToString("") { it.getTextInNode(source) }.lines()

    Column(width = Grow.Std) {
        modifier.padding(vertical = Dp(8f)).backgroundColor(style.codeBackgroundColor)

        text.forEach { lineStr ->
            if (lineStr.isNotEmpty()) {
                AttributedText(TextLine(listOf(lineStr to TextAttributes(style.codeFont, style.textColor)))) {
                    modifier.width(Grow.Std)
                }
            }
        }
    }
}

fun UiScope.MarkdownImage(node: ASTNode, source: String, style: MarkdownStyle) {
    val text = node.getTextInNode(source).toString()
    val urlStart = text.indexOf('(') + 1
    val urlEnd = text.lastIndexOf(')')

    if (urlStart > 0 && urlEnd > urlStart) {
        val url = text.substring(urlStart, urlEnd)

        // TODO: Здесь наверное стоит сделать разделение между https и ResourceLocation, но вообще вроде стандартный `Image(url) { ... }` должен и так схавать
        Text("Image: $url") {
            modifier.textColor(style.linkColor).padding(vertical = Dp(8f)).font(style.italicFont)
        }
    }
}

fun UiScope.MarkdownTable(node: ASTNode, source: String, style: MarkdownStyle) {
    val widthCache = remember { mutableStateOf<List<Dp>?>(null) }
    val alignCache = remember { mutableStateOf<List<AlignmentX>?>(null) }
    val prevNodeState = remember { mutableStateOf<ASTNode?>(null) }

    if (prevNodeState.value != node) {
        widthCache.set(measureTableColumns(node, source, style))
        alignCache.set(parseTableAlignments(node, source))
        prevNodeState.set(node)
    }

    val columnWidths = widthCache.use() ?: emptyList()
    val alignments = alignCache.use() ?: emptyList()

    val headerNode = node.children.find { it.type == GFMElementTypes.HEADER }
    val rowNodes = node.children.filter { it.type == GFMElementTypes.ROW }

    ScrollArea(
        width = Grow.Std,
        height = FitContent,
        withVerticalScrollbar = false,
        withHorizontalScrollbar = true,
        containerModifier = {
            it.background(RectBackground(Color.WHITE.withAlpha(0.1f))).border(RectBorder(style.tableBorderColor, Dp(1f)))
        }
    ) {
        Column {
            modifier
                .background(RectBackground(Color.WHITE.withAlpha(0.05f)))
                .border(RectBorder(style.tableBorderColor, Dp(1f)))

            if (headerNode != null) {
                MarkdownTableRow(headerNode, source, style, columnWidths, alignments, isHeader = true)
                Box(width = Grow.Std, height = Dp(1f)) { modifier.backgroundColor(style.tableBorderColor) }
            }

            rowNodes.forEachIndexed { index, row ->
                val bgColor = if (index % 2 == 0) style.tableEvenRowColor else style.tableOddRowColor
                MarkdownTableRow(row, source, style, columnWidths, alignments, isHeader = false, rowBackgroundColor = bgColor)
            }
        }
    }
}

private fun UiScope.MarkdownTableRow(
    node: ASTNode,
    source: String,
    style: MarkdownStyle,
    columnWidths: List<Dp>,
    alignments: List<AlignmentX>,
    isHeader: Boolean,
    rowBackgroundColor: Color? = null
) {
    Row {
        modifier.width(FitContent)
        if (rowBackgroundColor != null) {
            modifier.backgroundColor(rowBackgroundColor)
        }

        val cells = node.children.filter { it.type == GFMTokenTypes.CELL }

        columnWidths.forEachIndexed { index, width ->
            if (index > 0) {
                Box(width = Dp(1f), height = Grow.Std) {
                    modifier.backgroundColor(style.tableBorderColor.withAlpha(0.5f))
                }
            }

            val cellNode = cells.getOrNull(index)
            val alignment = alignments.getOrNull(index) ?: AlignmentX.Start

            val finalAlign = if (isHeader) AlignmentX.Center else alignment

            Box {
                modifier
                    .width(width)
                    .padding(horizontal = Dp(8f), vertical = Dp(6f))

                if (isHeader) {
                    modifier.backgroundColor(style.tableHeaderBgColor)
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
                            .textAlignX(finalAlign)
                    }
                }
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

private fun measureTableColumns(tableNode: ASTNode, source: String, style: MarkdownStyle): List<Dp> {
    val widths = mutableMapOf<Int, Float>()
    val font = style.bodyFont
    val boldFont = style.boldFont
    val cellPaddingPx = 18f

    fun processRow(rowNode: ASTNode, isHeader: Boolean) {
        val cells = rowNode.children.filter { it.type == GFMTokenTypes.CELL }
        cells.forEachIndexed { index, cellNode ->
            val spans = collectSpans(
                cellNode,
                source,
                TextAttributes(if (isHeader) boldFont else font, style.textColor),
                style
            )

            var textWidth = 0f
            spans.forEach { (text, attrs) ->
                textWidth += measureStringWidth(text, attrs.font)
            }

            val currentMax = widths.getOrElse(index) { 0f }
            widths[index] = max(currentMax, textWidth + cellPaddingPx)
        }
    }

    tableNode.children.find { it.type == GFMElementTypes.HEADER }?.let { processRow(it, true) }
    tableNode.children.filter { it.type == GFMElementTypes.ROW }.forEach { processRow(it, false) }

    val colCount = widths.keys.maxOrNull() ?: -1
    return (0..colCount).map { Dp(widths[it] ?: 100f) }
}

private fun wrapText(spans: List<Pair<String, TextAttributes>>, maxWidth: Float): List<TextLine> {
    val visualLines = mutableListOf<TextLine>()
    var currentLineSpans = mutableListOf<Pair<String, TextAttributes>>()
    var currentLineWidth = 0f

    val effectiveWidth = maxWidth - 1f

    for ((text, attrs) in spans) {
        val words = text.split(" ")

        for (i in words.indices) {
            val word = words[i]
            val wordWithSpace = if (i < words.size - 1) "$word " else word

            val measuredWidth = measureStringWidth(wordWithSpace, attrs.font)

            if (currentLineWidth + measuredWidth > effectiveWidth && currentLineWidth > 0) {
                visualLines.add(TextLine(sanitize(currentLineSpans)))
                currentLineSpans = mutableListOf()
                currentLineWidth = 0f
            }

            currentLineSpans.add(wordWithSpace to attrs)
            currentLineWidth += measuredWidth
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

// TODO: Тут пока довольно сомнительная реализация, она не учитывает вложенные виджеты (например, одновременно жирный шрифт, курсив и цвет + выделение фона)
//  Кроме того, тут слишком плохая обработка неправильных выражений, например, когда есть одна звёздочка (*), а закрывающей нет. Да и символ `\` тоже нужно перепроверить, везде ли он работает
private fun collectSpans(
    node: ASTNode,
    source: String,
    currentAttr: TextAttributes,
    style: MarkdownStyle,
): List<Pair<String, TextAttributes>> {
    val result = mutableListOf<Pair<String, TextAttributes>>()

    if (node.children.isEmpty()) {
        val text = node.getTextInNode(source).toString().replace("\\", "")
        result.add(text to currentAttr)
    } else {
        node.children.forEach { child ->
            when (child.type.toString()) {
                "Markdown:INLINE_LINK" -> {
                    val destinationNode = child.children.find { it.type.toString() == "Markdown:LINK_DESTINATION" }
                    val textNode = child.children.find { it.type.toString() == "Markdown:LINK_TEXT" }

                    val destination = destinationNode?.getTextInNode(source)?.toString() ?: ""

                    if (destination.startsWith("color:")) {
                        val colorName = destination.removePrefix("color:")
                        val color = parseColorString(colorName) ?: currentAttr.color

                        textNode?.children?.getOrNull(1)?.let {
                            result.addAll(collectSpans(it, source, currentAttr.copy(color = color), style))
                        }
                    } else {
                        textNode?.children?.getOrNull(1)?.let {
                            result.addAll(collectSpans(it, source, currentAttr.copy(color = style.linkColor), style))
                        }
                    }
                }

                "Markdown:STRONG" -> {
                    if (child.children.isEmpty()) {
                        result.addAll(collectSpans(child, source, currentAttr, style))
                    } else {
                        child.children.getOrNull(2)?.let {
                            result.addAll(collectSpans(it, source, currentAttr.copy(font = style.boldFont), style))
                        }
                    }
                }

                "Markdown:EMPH" -> {
                    if (child.children.isEmpty()) {
                        result.addAll(collectSpans(child, source, currentAttr, style))
                    } else {
                        child.children.getOrNull(1)?.let {
                            result.addAll(collectSpans(it, source, currentAttr.copy(font = style.italicFont), style))
                        }
                    }
                }

                "Markdown:CODE_SPAN" -> {
                    val attributes = currentAttr.copy(font = style.codeFont, background = style.codeBackgroundColor)
                    if (child.children.isEmpty()) {
                        result.addAll(collectSpans(child, source, attributes, style))
                    } else {
                        child.children.getOrNull(1)?.let {
                            result.addAll(collectSpans(it, source, attributes, style))
                        }
                    }
                }

                else -> result.addAll(collectSpans(child, source, currentAttr, style))
            }
        }
    }
    return result
}

private fun sanitize(spans: List<Pair<String, TextAttributes>>): List<Pair<String, TextAttributes>> {
    val newSpans = mutableListOf<Pair<String, TextAttributes>>()
    if (spans.isNotEmpty()) {
        var prevSpan = spans[0]
        newSpans += prevSpan
        for (i in 1 until spans.size) {
            val span = spans[i]
            if (span.second == prevSpan.second) {
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