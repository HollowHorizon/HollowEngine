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
import ru.hollowhorizon.hollowengine.client.gui.markdown.components.FlowText
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.util.TextAttributes
import ru.hollowhorizon.hollowengine.client.kool.minecraft.Image


fun UiScope.MarkdownImage(node: ASTNode, source: String, style: MarkdownStyle) {
    val text = node.getTextInNode(source).toString()
    val urlStart = text.indexOf('(') + 1
    val urlEnd = text.lastIndexOf(')')

    if (urlStart > 0 && urlEnd > urlStart) {
        val rawUrl = text.substring(urlStart, urlEnd)

        Image(rawUrl) {
            modifier
                .padding(vertical = Dp(8f))
                .width(Grow.Std).height(150.dp)
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

            FlowText(spans) {
                modifier
                    .width(Grow.Std)
                    .flowAlignX(alignment)
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

private fun collectSpans(
    node: ASTNode,
    source: String,
    parentAttr: TextAttributes,
    style: MarkdownStyle,
): List<Pair<String, TextAttributes>> {
    val result = mutableListOf<Pair<String, TextAttributes>>()

    fun visit(currentNode: ASTNode, currentAttr: TextAttributes) {
        when (currentNode.type) {
            MarkdownElementTypes.STRONG -> {
                currentNode.children.forEach {
                    visit(
                        it,
                        currentAttr.copy(font = currentAttr.font.copy(weight = MsdfFont.WEIGHT_EXTRA_BOLD))
                    )
                }
            }

            MarkdownElementTypes.EMPH -> {
                currentNode.children.forEach {
                    visit(
                        it,
                        currentAttr.copy(font = currentAttr.font.copy(italic = MsdfFont.ITALIC_STD))
                    )
                }
            }

            MarkdownElementTypes.INLINE_LINK -> {
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

                textNode?.children?.forEach { child ->
                    if (child.type != MarkdownTokenTypes.LBRACKET && child.type != MarkdownTokenTypes.RBRACKET) {
                        visit(child, linkAttr)
                    }
                }
            }

            MarkdownElementTypes.CODE_SPAN -> {
                val codeAttr = currentAttr.copy(font = style.codeFont, background = style.codeBackgroundColor)
                currentNode.children.forEach { child ->
                    if (child.type == MarkdownTokenTypes.TEXT || child.type == MarkdownTokenTypes.CODE_FENCE_CONTENT) {
                        result.add(child.getTextInNode(source).toString() to codeAttr)
                    }
                }
            }

            MarkdownTokenTypes.TEXT,
            MarkdownTokenTypes.WHITE_SPACE,
            MarkdownTokenTypes.COLON,
            MarkdownTokenTypes.LPAREN,
            MarkdownTokenTypes.RPAREN,
            MarkdownTokenTypes.LBRACKET,
            MarkdownTokenTypes.RBRACKET,
                -> {
                val text = currentNode.getTextInNode(source).toString()
                val unescaped = unescapeMarkdown(text)
                result.add(unescaped to currentAttr)
            }

            MarkdownTokenTypes.ESCAPED_BACKTICKS -> {
                result.add("`" to currentAttr)
            }

            else -> {
                currentNode.children.forEach { visit(it, currentAttr) }
            }
        }
    }

    visit(node, parentAttr)
    return result
}

private fun unescapeMarkdown(text: String): String {
    if (!text.contains('\\')) return text
    val sb = StringBuilder(text.length)
    var escaped = false
    for (c in text) {
        if (escaped) {
            escaped = false
        } else if (c == '\\') {
            escaped = true
            continue
        }
        sb.append(c)
    }
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