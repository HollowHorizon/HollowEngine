package ru.hollowhorizon.hollowengine.client.gui.markdown.components

import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.MsdfFont
import org.intellij.markdown.ast.ASTNode
import ru.hollowhorizon.hollowengine.client.gui.colors.ColorTheme
import ru.hollowhorizon.hollowengine.client.gui.markdown.MarkdownStyle

fun UiScope.MarkdownText(
    node: ASTNode,
    content: String,
    style: MarkdownStyle
) {
    val spans = with(content) {
        with(style) {
            buildMarkdownSpans(node)
        }
    }

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

fun UiScope.MarkdownText(text: String, style: MarkdownStyle, body: UiScope.() -> Unit = {}) {
    Column(Grow.Std) {
        modifier.padding(bottom = Dp(16f))

        val maxWidth = remember(0f)

        modifier.onMeasured {
            maxWidth.set(it.innerWidthPx)
        }

        val visualLines = wrapText(listOf(text to TextAttributes(style.bodyFont, ColorTheme.UI.WhiteReplacement)), maxWidth.use())

        visualLines.forEach { line ->
            AttributedText(line) {
                modifier.width(Grow.Std)
                body()
            }
        }
    }
}

fun wrapText(spans: List<Pair<String, TextAttributes>>, maxWidth: Float): List<TextLine> {
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