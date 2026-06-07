package ru.hollowhorizon.hollowengine.client.ui

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.network.chat.Component
import java.util.LinkedHashMap
import kotlin.math.abs

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
    val sourceLength: Int = text.length,
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
    private const val MaxCachedLayouts = 512
    private const val MaxCachedTextWidths = 2048
    private val lineCache = lruCache<LineCacheKey, List<RawTextLine>>(MaxCachedLayouts)
    private val layoutCache = lruCache<LayoutCacheKey, UiTextLayout>(MaxCachedLayouts)
    private val widthCache = lruCache<TextWidthCacheKey, Float>(MaxCachedTextWidths)

    fun measure(
        text: String,
        availableWidth: Float,
        knownWidth: Float?,
        wrap: Boolean,
        fontSize: Float,
        preserveWhitespace: Boolean = false,
    ): LayoutSize = measure(UiRichText.plain(text), availableWidth, knownWidth, wrap, fontSize, preserveWhitespace)

    fun measure(
        richText: UiRichText,
        availableWidth: Float,
        knownWidth: Float?,
        wrap: Boolean,
        fontSize: Float,
        preserveWhitespace: Boolean = false,
    ): LayoutSize {
        if (knownWidth != null) {
            val layout = layout(richText, knownWidth, Float.POSITIVE_INFINITY, wrap, UiTextAlign.LEFT, fontSize, preserveWhitespace)
            return LayoutSize(knownWidth, layout.height)
        }
        val naturalWidth = buildLines(richText, Float.POSITIVE_INFINITY, wrap = false, fontSize, preserveWhitespace)
            .maxOfOrNull { it.width } ?: 0f
        val width = knownWidth
            ?: availableWidth.takeIf { wrap && it > 0f }?.let { minOf(naturalWidth, it) }
            ?: naturalWidth
        val layout = layout(richText, width, Float.POSITIVE_INFINITY, wrap, UiTextAlign.LEFT, fontSize, preserveWhitespace)
        return LayoutSize(width, layout.height)
    }

    fun layout(
        text: String,
        width: Float,
        height: Float,
        wrap: Boolean,
        align: UiTextAlign,
        fontSize: Float,
        preserveWhitespace: Boolean = false,
    ): UiTextLayout = layout(UiRichText.plain(text), width, height, wrap, align, fontSize, preserveWhitespace)

    fun layout(
        richText: UiRichText,
        width: Float,
        height: Float,
        wrap: Boolean,
        align: UiTextAlign,
        fontSize: Float,
        preserveWhitespace: Boolean = false,
    ): UiTextLayout {
        val key = LayoutCacheKey(richText, width.cacheValue(), height.cacheValue(), wrap, align, fontSize.cacheValue(), preserveWhitespace, fontSignature())
        layoutCache[key]?.let { return it }
        val rawLines = buildLines(richText, width, wrap, fontSize, preserveWhitespace)
        val lines = mutableListOf<UiTextLine>()
        var y = 0f
        for (raw in rawLines) {
            if (height.isFinite() && lines.isNotEmpty() && y + raw.height > height) break
            val justify = align == UiTextAlign.JUSTIFY && !raw.lastInParagraph && raw.justifyGapCount > 0
            val extra = if (justify) {
                (width - raw.width).coerceAtLeast(0f) / raw.justifyGapCount.coerceAtLeast(1)
            } else {
                0f
            }
            val x = if (justify) 0f else align.lineOffset(width, raw.width)
            lines += raw.toLine(x, y, width, justify, extra)
            y += raw.height
            if (height.isFinite() && y >= height) break
        }
        return UiTextLayout(lines, width, y).also { layoutCache[key] = it }
    }

    fun measureTextWidth(text: String, fontSize: Float): Float {
        return measureTextWidth(text, fontSize, UiInlineStyle())
    }

    fun visibleTextPrefix(layout: UiTextLayout, visibleCharacters: Int, fontSize: Float): UiTextLayout {
        var remaining = visibleCharacters.coerceAtLeast(0)
        val lines = layout.lines.mapNotNull { line ->
            val fragments = mutableListOf<UiTextFragment>()
            var lineText = StringBuilder()
            var cursor = 0
            fragmentLoop@ for (fragment in line.fragments) {
                when (fragment) {
                    is UiInlineImageRun -> fragments += fragment
                    is UiTextRun -> {
                        val start = line.text.indexOf(fragment.text, cursor).takeIf { it >= 0 } ?: cursor
                        remaining -= (start - cursor).coerceAtLeast(0)
                        if (remaining <= 0) break@fragmentLoop
                        val visible = fragment.text.take(remaining)
                        if (visible.isEmpty()) continue@fragmentLoop
                        val size = fragment.style.resolvedFontSize(fontSize)
                        fragments += fragment.copy(
                            text = visible,
                            width = measureTextWidth(visible, size, fragment.style),
                        )
                        lineText.append(visible)
                        remaining -= visible.length
                        cursor = start + fragment.text.length
                    }
                }
            }
            remaining -= (line.sourceLength - cursor).coerceAtLeast(0)
            if (fragments.isEmpty()) return@mapNotNull null
            line.copy(text = lineText.toString(), fragments = fragments)
        }
        val height = lines.lastOrNull()?.let { it.y + it.height } ?: 0f
        return layout.copy(lines = lines, height = height)
    }

    private fun measureTextWidth(text: String, fontSize: Float, style: UiInlineStyle): Float {
        val key = TextWidthCacheKey(text, style, fontSize.cacheValue(), fontSignature())
        widthCache[key]?.let { return it }
        val width = font?.width(text.component(style))?.toFloat()
            ?: (text.length * (EstimatedGlyphWidth + if (style.bold) 1f else 0f))
        return (width * (fontSize / (font?.lineHeight?.toFloat() ?: DefaultUiFontSize))).also { widthCache[key] = it }
    }

    private fun buildLines(
        richText: UiRichText,
        width: Float,
        wrap: Boolean,
        fontSize: Float,
        preserveWhitespace: Boolean,
    ): List<RawTextLine> {
        val key = LineCacheKey(richText, width.cacheValue(), wrap, fontSize.cacheValue(), preserveWhitespace, fontSignature())
        lineCache[key]?.let { return it }
        val units = richText.toUnits(fontSize)
        val result = mutableListOf<RawTextLine>()
        val current = mutableListOf<InlineUnit>()
        var currentWidth = 0f
        var pendingSpace: InlineUnit.Space? = null
        var endedWithNewline = false

        fun commit(lastInParagraph: Boolean, trailingSourceCharacters: Int = 0) {
            result += RawTextLine(current.toList(), lastInParagraph, trailingSourceCharacters)
            current.clear()
            currentWidth = 0f
            pendingSpace = null
        }

        unitLoop@ for (unit in units) {
            when (unit) {
                InlineUnit.Newline -> {
                    commit(lastInParagraph = true, trailingSourceCharacters = 1)
                    endedWithNewline = true
                }

                is InlineUnit.Space -> {
                    endedWithNewline = false
                    if (preserveWhitespace) {
                        if (wrap && current.isNotEmpty() && currentWidth + unit.width > width) {
                            commit(lastInParagraph = false)
                        }
                        current += unit
                        currentWidth += unit.width
                    } else if (current.isNotEmpty()) {
                        pendingSpace = unit
                    }
                }

                is InlineUnit.Word -> {
                    endedWithNewline = false
                    val space = pendingSpace
                    if (wrap && width.isFinite() && width > 0f && unit.width > width) {
                        if (current.isNotEmpty()) {
                            commit(lastInParagraph = false, trailingSourceCharacters = if (space == null) 0 else 1)
                        }
                        val chunks = splitOversizedWord(unit, width)
                        chunks.forEachIndexed { index, chunk ->
                            current += chunk
                            currentWidth = chunk.width
                            if (index < chunks.lastIndex) commit(lastInParagraph = false)
                        }
                        pendingSpace = null
                        continue@unitLoop
                    }

                    val candidateWidth = currentWidth + (space?.width ?: 0f) + unit.width
                    if (wrap && current.isNotEmpty() && candidateWidth > width) {
                        commit(lastInParagraph = false, trailingSourceCharacters = if (space == null) 0 else 1)
                    } else if (space != null) {
                        current += space
                        currentWidth += space.width
                    }
                    current += unit
                    currentWidth += unit.width
                    pendingSpace = null
                }

                is InlineUnit.Image -> {
                    endedWithNewline = false
                    val space = pendingSpace
                    val candidateWidth = currentWidth + (space?.width ?: 0f) + unit.width
                    if (wrap && current.isNotEmpty() && candidateWidth > width) {
                        commit(lastInParagraph = false, trailingSourceCharacters = if (space == null) 0 else 1)
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
        return result.also { lineCache[key] = it }
    }

    private fun fontSignature(): Int {
        val activeFont = font
        return 31 * System.identityHashCode(activeFont) + (activeFont?.lineHeight ?: DefaultUiFontSize.toInt())
    }

    private fun splitOversizedWord(word: InlineUnit.Word, width: Float): List<InlineUnit.Word> {
        val chunks = mutableListOf<InlineUnit.Word>()
        val buffer = StringBuilder()
        var bufferWidth = 0f

        fun flush() {
            if (buffer.isEmpty()) return
            chunks += InlineUnit.Word(buffer.toString(), word.style, bufferWidth, word.height)
            buffer.setLength(0)
            bufferWidth = 0f
        }

        for (char in word.text) {
            val text = char.toString()
            val charWidth = measureTextWidth(text, word.height, word.style)
            if (buffer.isNotEmpty() && bufferWidth + charWidth > width) flush()
            buffer.append(char)
            bufferWidth += charWidth
            if (bufferWidth > width) flush()
        }
        flush()
        return chunks.ifEmpty { listOf(word) }
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
            sourceLength = sourceLength,
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
        val trailingSourceCharacters: Int = 0,
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
        val sourceLength: Int = text.length + trailingSourceCharacters
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

    private data class LineCacheKey(
        val richText: UiRichText,
        val width: Float,
        val wrap: Boolean,
        val fontSize: Float,
        val preserveWhitespace: Boolean,
        val fontSignature: Int,
    )

    private data class LayoutCacheKey(
        val richText: UiRichText,
        val width: Float,
        val height: Float,
        val wrap: Boolean,
        val align: UiTextAlign,
        val fontSize: Float,
        val preserveWhitespace: Boolean,
        val fontSignature: Int,
    )

    private data class TextWidthCacheKey(
        val text: String,
        val style: UiInlineStyle,
        val fontSize: Float,
        val fontSignature: Int,
    )
}

private fun Float.cacheValue(): Float {
    if (!isFinite()) return this
    return (this * 100f).toInt().toFloat() / 100f
}

private fun <K, V> lruCache(maxSize: Int): MutableMap<K, V> {
    return object : LinkedHashMap<K, V>(maxSize, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean = size > maxSize
    }
}

fun UiTextLayout.caretPosition(index: Int, fontSize: Float): UiVec3 {
    if (lines.isEmpty()) return UiVec3()
    var consumed = 0
    for (line in lines) {
        val lineEnd = consumed + line.sourceLength
        if (index < lineEnd || line === lines.last()) {
            val local = (index - consumed).coerceIn(0, line.sourceLength)
            return UiVec3(line.xAt(local, fontSize), line.y)
        }
        consumed = lineEnd
    }
    val last = lines.last()
    return UiVec3(last.xAt(last.sourceLength, fontSize), last.y)
}

fun UiTextLayout.caretIndexAt(x: Float, y: Float, fontSize: Float): Int {
    if (lines.isEmpty()) return 0
    val line = lines.minBy { line ->
        when {
            y < line.y -> line.y - y
            y > line.y + line.height -> y - (line.y + line.height)
            else -> 0f
        }
    }
    var bestOffset = 0
    var bestDistance = Float.POSITIVE_INFINITY
    for (offset in 0..line.sourceLength) {
        val distance = abs(line.xAt(offset, fontSize) - x)
        if (distance < bestDistance) {
            bestDistance = distance
            bestOffset = offset
        }
    }
    return lines.takeWhile { it !== line }.sumOf { it.sourceLength } + bestOffset
}

fun UiTextLayout.selectionRects(start: Int, end: Int, fontSize: Float): List<UiRect> {
    val selectionStart = minOf(start, end).coerceAtLeast(0)
    val selectionEnd = maxOf(start, end).coerceAtLeast(selectionStart)
    if (selectionStart == selectionEnd) return emptyList()
    val rects = mutableListOf<UiRect>()
    var consumed = 0
    for (line in lines) {
        val lineStart = consumed
        val lineEnd = consumed + line.sourceLength
        val startOffset = (selectionStart - lineStart).coerceIn(0, line.sourceLength)
        val endOffset = (selectionEnd - lineStart).coerceIn(0, line.sourceLength)
        if (selectionStart < lineEnd && selectionEnd > lineStart && startOffset != endOffset) {
            val x1 = line.xAt(startOffset, fontSize)
            val x2 = line.xAt(endOffset, fontSize)
            rects += UiRect(minOf(x1, x2), line.y, abs(x2 - x1), line.height)
        }
        consumed = lineEnd
    }
    return rects
}

private fun UiTextLine.xAt(offset: Int, fontSize: Float): Float {
    val textOffset = offset.coerceIn(0, text.length)
    val prefix = text.take(textOffset)
    return x + UiTextLayouter.measureTextWidth(prefix, fontSize)
}
