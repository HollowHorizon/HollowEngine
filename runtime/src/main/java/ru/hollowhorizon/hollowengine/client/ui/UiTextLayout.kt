package ru.hollowhorizon.hollowengine.client.ui

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font

data class UiTextLayout(
    val lines: List<UiTextLine>,
    val width: Float,
    val height: Float,
)

data class UiTextLine(
    val text: String,
    val x: Float,
    val y: Float,
    val width: Float,
    val naturalWidth: Float,
    val height: Float,
    val justify: Boolean = false,
    val extraSpace: Float = 0f,
)

private val font: Font?
    get() = runCatching { Minecraft.getInstance()?.font }.getOrNull()

internal object UiTextLayouter {
    private const val EstimatedGlyphWidth = 6f

    fun measure(
        text: String,
        availableWidth: Float,
        knownWidth: Float?,
        wrap: Boolean,
        fontSize: Float,
    ): LayoutSize {
        val naturalWidth = measureTextWidth(text.longestExplicitLine(), fontSize)
        val width = knownWidth
            ?: availableWidth.takeIf { wrap && it > 0f }?.let { minOf(naturalWidth, it) }
            ?: naturalWidth
        val layout = layout(text, width, Float.POSITIVE_INFINITY, wrap, UiTextAlign.LEFT, fontSize)
        return LayoutSize(width, layout.height)
    }

    fun layout(
        text: String,
        width: Float,
        height: Float,
        wrap: Boolean,
        align: UiTextAlign,
        fontSize: Float,
    ): UiTextLayout {
        val lineHeight = fontSize.coerceAtLeast(0.0001f)
        val rawLines = buildLines(text, width, wrap, fontSize)
        val maxLines = if (height.isFinite()) {
            (height / lineHeight).toInt().coerceAtLeast(1)
        } else {
            Int.MAX_VALUE
        }
        val lines = rawLines.take(maxLines).mapIndexed { index, raw ->
            val justify = align == UiTextAlign.JUSTIFY && !raw.lastInParagraph && raw.wordCount > 1
            val naturalWidth = measureTextWidth(raw.text, fontSize)
            val extra = if (justify) {
                ((width - naturalWidth).coerceAtLeast(0f) / (raw.wordCount - 1).coerceAtLeast(1))
            } else {
                0f
            }
            UiTextLine(
                text = raw.text,
                x = if (justify) 0f else align.lineOffset(width, naturalWidth),
                y = index * lineHeight,
                width = if (justify) width else naturalWidth,
                naturalWidth = naturalWidth,
                height = lineHeight,
                justify = justify,
                extraSpace = extra,
            )
        }
        return UiTextLayout(lines, width, lines.size * lineHeight)
    }

    fun measureTextWidth(text: String, fontSize: Float): Float {
        return (font?.width(text)?.toFloat() ?: (text.length * EstimatedGlyphWidth)) *
                (fontSize / (font?.lineHeight?.toFloat() ?: DefaultUiFontSize))
    }

    private fun buildLines(text: String, width: Float, wrap: Boolean, fontSize: Float): List<RawTextLine> {
        if (!wrap) {
            return text.split('\n').mapIndexed { index, line ->
                RawTextLine(line, lastInParagraph = true, forcedBreak = index < text.count { it == '\n' })
            }
        }
        val result = mutableListOf<RawTextLine>()
        val paragraphs = text.split('\n')
        paragraphs.forEachIndexed { paragraphIndex, paragraph ->
            val words = paragraph.split(' ').filter { it.isNotEmpty() }
            if (words.isEmpty()) {
                result += RawTextLine("", lastInParagraph = true, forcedBreak = paragraphIndex < paragraphs.lastIndex)
            } else {
                var current = ""
                for (word in words) {
                    val candidate = if (current.isEmpty()) word else "$current $word"
                    if (current.isNotEmpty() && measureTextWidth(candidate, fontSize) > width) {
                        result += RawTextLine(current, lastInParagraph = false)
                        current = word
                    } else {
                        current = candidate
                    }
                }
                result += RawTextLine(
                    current,
                    lastInParagraph = true,
                    forcedBreak = paragraphIndex < paragraphs.lastIndex
                )
            }
        }
        return result.ifEmpty { listOf(RawTextLine("", lastInParagraph = true)) }
    }

    private fun UiTextAlign.lineOffset(width: Float, lineWidth: Float): Float = when (this) {
        UiTextAlign.LEFT, UiTextAlign.JUSTIFY -> 0f
        UiTextAlign.RIGHT -> (width - lineWidth).coerceAtLeast(0f)
        UiTextAlign.CENTER -> ((width - lineWidth) / 2f).coerceAtLeast(0f)
    }

    private fun String.longestExplicitLine(): String = split('\n').maxByOrNull { it.length } ?: ""

    private data class RawTextLine(
        val text: String,
        val lastInParagraph: Boolean,
        val forcedBreak: Boolean = false,
    ) {
        val wordCount: Int get() = text.split(' ').count { it.isNotEmpty() }
    }
}
