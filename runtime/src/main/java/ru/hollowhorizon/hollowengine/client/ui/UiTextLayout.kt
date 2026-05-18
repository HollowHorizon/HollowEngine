package ru.hollowhorizon.hollowengine.client.ui

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.network.chat.Component

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
    val fragments: List<UiTextFragment> = emptyList(),
)

sealed interface UiTextFragment {
    val x: Float
    val y: Float
    val width: Float
    val height: Float
}

data class UiTextRun(
    val text: String,
    val style: UiInlineStyle,
    override val x: Float,
    override val y: Float,
    override val width: Float,
    override val height: Float,
) : UiTextFragment

data class UiInlineImageRun(
    val image: UiInlineItem.Image,
    override val x: Float,
    override val y: Float,
    override val width: Float,
    override val height: Float,
) : UiTextFragment

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
    ): LayoutSize = measure(UiRichTextParser.parse(text), availableWidth, knownWidth, wrap, fontSize)

    fun measure(
        richText: UiRichText,
        availableWidth: Float,
        knownWidth: Float?,
        wrap: Boolean,
        fontSize: Float,
    ): LayoutSize {
        val naturalWidth = buildLines(richText, Float.POSITIVE_INFINITY, wrap = false, fontSize)
            .maxOfOrNull { it.width } ?: 0f
        val width = knownWidth
            ?: availableWidth.takeIf { wrap && it > 0f }?.let { minOf(naturalWidth, it) }
            ?: naturalWidth
        val layout = layout(richText, width, Float.POSITIVE_INFINITY, wrap, UiTextAlign.LEFT, fontSize)
        return LayoutSize(width, layout.height)
    }

    fun layout(
        text: String,
        width: Float,
        height: Float,
        wrap: Boolean,
        align: UiTextAlign,
        fontSize: Float,
    ): UiTextLayout = layout(UiRichTextParser.parse(text), width, height, wrap, align, fontSize)

    fun layout(
        richText: UiRichText,
        width: Float,
        height: Float,
        wrap: Boolean,
        align: UiTextAlign,
        fontSize: Float,
    ): UiTextLayout {
        val rawLines = buildLines(richText, width, wrap, fontSize)
        val lines = mutableListOf<UiTextLine>()
        var y = 0f
        for (raw in rawLines) {
            if (height.isFinite() && lines.isNotEmpty() && y + raw.height > height) break
            val justify = align == UiTextAlign.JUSTIFY && !raw.lastInParagraph && raw.justifyGapCount > 0
            val extra = if (justify) {
                ((width - raw.width).coerceAtLeast(0f) / raw.justifyGapCount.coerceAtLeast(1))
            } else {
                0f
            }
            val x = if (justify) 0f else align.lineOffset(width, raw.width)
            lines += raw.toLine(x, y, width, justify, extra)
            y += raw.height
            if (height.isFinite() && y >= height) break
        }
        return UiTextLayout(lines, width, y)
    }

    fun measureTextWidth(text: String, fontSize: Float): Float {
        return measureTextWidth(text, fontSize, UiInlineStyle())
    }

    private fun measureTextWidth(text: String, fontSize: Float, style: UiInlineStyle): Float {
        val width = font?.width(text.component(style))?.toFloat()
            ?: (text.length * (EstimatedGlyphWidth + if (style.bold) 1f else 0f))
        return width * (fontSize / (font?.lineHeight?.toFloat() ?: DefaultUiFontSize))
    }

    private fun buildLines(richText: UiRichText, width: Float, wrap: Boolean, fontSize: Float): List<RawTextLine> {
        val units = richText.toUnits(fontSize)
        val result = mutableListOf<RawTextLine>()
        val current = mutableListOf<InlineUnit>()
        var currentWidth = 0f
        var pendingSpace: InlineUnit.Space? = null
        var endedWithNewline = false

        fun commit(lastInParagraph: Boolean) {
            result += RawTextLine(current.toList(), lastInParagraph)
            current.clear()
            currentWidth = 0f
            pendingSpace = null
        }

        for (unit in units) {
            when (unit) {
                InlineUnit.Newline -> {
                    commit(lastInParagraph = true)
                    endedWithNewline = true
                }

                is InlineUnit.Space -> if (current.isNotEmpty()) pendingSpace = unit
                is InlineUnit.Word, is InlineUnit.Image -> {
                    endedWithNewline = false
                    val space = pendingSpace
                    val candidateWidth = currentWidth + (space?.width ?: 0f) + unit.width
                    if (wrap && current.isNotEmpty() && candidateWidth > width) {
                        commit(lastInParagraph = false)
                    } else if (space != null) {
                        current += space
                        currentWidth += space.width
                    }
                    current += unit
                    currentWidth += unit.width
                    pendingSpace = null
                }
            }
        }
        if (current.isNotEmpty() || result.isEmpty() || endedWithNewline) commit(lastInParagraph = true)
        return result
    }

    private fun UiRichText.toUnits(baseFontSize: Float): List<InlineUnit> {
        val units = mutableListOf<InlineUnit>()
        for (item in items) {
            when (item) {
                is UiInlineItem.Image -> units += InlineUnit.Image(item)
                is UiInlineItem.Text -> item.value.toUnits(item.style, baseFontSize, units)
            }
        }
        return units
    }

    private fun String.toUnits(style: UiInlineStyle, baseFontSize: Float, target: MutableList<InlineUnit>) {
        val buffer = StringBuilder()
        fun flushWord() {
            if (buffer.isEmpty()) return
            val text = buffer.toString()
            val size = style.resolvedFontSize(baseFontSize)
            target += InlineUnit.Word(text, style, measureTextWidth(text, size, style), size)
            buffer.setLength(0)
        }
        for (char in this) {
            when (char) {
                '\n' -> {
                    flushWord()
                    target += InlineUnit.Newline
                }

                ' ', '\t' -> {
                    flushWord()
                    val size = style.resolvedFontSize(baseFontSize)
                    target += InlineUnit.Space(style, measureTextWidth(" ", size, style), size)
                }

                '\u00A0' -> buffer.append(' ')
                else -> buffer.append(char)
            }
        }
        flushWord()
    }

    private fun RawTextLine.toLine(
        lineX: Float,
        lineY: Float,
        textBoxWidth: Float,
        justify: Boolean,
        extraSpace: Float,
    ): UiTextLine {
        var x = lineX
        val fragments = mutableListOf<UiTextFragment>()
        for (unit in units) {
            when (unit) {
                InlineUnit.Newline -> Unit
                is InlineUnit.Space -> x += unit.width + if (justify) extraSpace else 0f
                is InlineUnit.Word -> {
                    fragments += UiTextRun(unit.text, unit.style, x, unit.y, unit.width, unit.height)
                    x += unit.width
                }

                is InlineUnit.Image -> {
                    fragments += UiInlineImageRun(unit.image, x, unit.y, unit.width, unit.height)
                    x += unit.width
                }
            }
        }
        return UiTextLine(
            text = text,
            x = lineX,
            y = lineY,
            width = if (justify) textBoxWidth else width,
            naturalWidth = width,
            height = height,
            justify = justify,
            extraSpace = extraSpace,
            fragments = fragments,
        )
    }

    private fun UiTextAlign.lineOffset(width: Float, lineWidth: Float): Float = when (this) {
        UiTextAlign.LEFT, UiTextAlign.JUSTIFY -> 0f
        UiTextAlign.RIGHT -> (width - lineWidth).coerceAtLeast(0f)
        UiTextAlign.CENTER -> ((width - lineWidth) / 2f).coerceAtLeast(0f)
    }

    private data class RawTextLine(
        val units: List<InlineUnit>,
        val lastInParagraph: Boolean,
    ) {
        val text: String = buildString {
            for (unit in units) {
                when (unit) {
                    InlineUnit.Newline -> Unit
                    is InlineUnit.Space -> append(' ')
                    is InlineUnit.Word -> append(unit.text)
                    is InlineUnit.Image -> append(unit.image.alt.ifBlank { "\uFFFC" })
                }
            }
        }
        val width: Float = units.sumOf { it.width.toDouble() }.toFloat()
        val height: Float
        val justifyGapCount: Int = units.count { it is InlineUnit.Space }

        init {
            val textHeight = units.filterIsInstance<InlineUnit.Word>().maxOfOrNull { it.height } ?: DefaultUiFontSize
            val lineHeight = units.maxOfOrNull { it.height } ?: textHeight
            val imageBaseline = units.filterIsInstance<InlineUnit.Image>().maxOfOrNull { image ->
                if (image.image.align == UiInlineAlign.BASELINE) image.height else 0f
            } ?: 0f
            val baseline = maxOf(textHeight, imageBaseline)
            units.forEach { it.resolveY(lineHeight, baseline) }
            height = lineHeight
        }
    }

    private sealed interface InlineUnit {
        val width: Float
        val height: Float
        var y: Float

        data object Newline : InlineUnit {
            override val width: Float = 0f
            override val height: Float = 0f
            override var y: Float = 0f
        }

        data class Space(
            val style: UiInlineStyle,
            override val width: Float,
            override val height: Float,
        ) : InlineUnit {
            override var y: Float = 0f
        }

        data class Word(
            val text: String,
            val style: UiInlineStyle,
            override val width: Float,
            override val height: Float,
        ) : InlineUnit {
            override var y: Float = 0f
        }

        data class Image(
            val image: UiInlineItem.Image,
        ) : InlineUnit {
            override val width: Float = image.width
            override val height: Float = image.height
            override var y: Float = 0f
        }
    }

    private fun InlineUnit.resolveY(lineHeight: Float, baseline: Float) {
        y = when (this) {
            InlineUnit.Newline, is InlineUnit.Space -> 0f
            is InlineUnit.Word -> (lineHeight - height) / 2f
            is InlineUnit.Image -> when (image.align) {
                UiInlineAlign.BASELINE -> baseline - height
                UiInlineAlign.MIDDLE -> (lineHeight - height) / 2f
                UiInlineAlign.TOP -> 0f
                UiInlineAlign.BOTTOM -> lineHeight - height
            }
        }.coerceAtLeast(0f)
    }

    private fun String.component(style: UiInlineStyle): Component {
        return Component.literal(this).withStyle {
            it.withBold(style.bold)
                .withItalic(style.italic)
                .withUnderlined(style.underline || style.link != null)
                .withStrikethrough(style.strikethrough)
        }
    }
}
