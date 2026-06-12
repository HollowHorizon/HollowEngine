package ru.hollowhorizon.hollowengine.client.ui

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

data class UiTextSpaceRun(
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

data class UiInlineWidgetRun(
    val widget: UiInlineItem.Widget,
    override val x: Float,
    override val y: Float,
    override val width: Float,
    override val height: Float,
) : UiTextFragment

internal object UiTextLayouter {
    private const val MaxCachedLayouts = 512
    private const val MaxCachedTextWidths = 2048
    private const val FloatingWidgetGap = 6f
    private val lineCache = lruCache<LineCacheKey, List<RawTextLine>>(MaxCachedLayouts)
    private val layoutCache = lruCache<LayoutCacheKey, UiTextLayout>(MaxCachedLayouts)
    private val widthCache = lruCache<TextWidthCacheKey, Float>(MaxCachedTextWidths)

    fun measure(
        text: String,
        availableWidth: Float,
        knownWidth: Float?,
        wrap: Boolean,
        fontSize: Float,
        fontFamily: String? = null,
        preserveWhitespace: Boolean = false,
        lineSpacing: Float = 0f,
        spaceWidth: Float? = null,
    ): LayoutSize = measure(
        UiRichText.plain(text),
        availableWidth,
        knownWidth,
        wrap,
        fontSize,
        fontFamily,
        preserveWhitespace,
        lineSpacing,
        spaceWidth,
    )

    fun measure(
        richText: UiRichText,
        availableWidth: Float,
        knownWidth: Float?,
        wrap: Boolean,
        fontSize: Float,
        fontFamily: String? = null,
        preserveWhitespace: Boolean = false,
        lineSpacing: Float = 0f,
        spaceWidth: Float? = null,
    ): LayoutSize {
        if (knownWidth != null) {
            val layout = layout(
                richText,
                knownWidth,
                Float.POSITIVE_INFINITY,
                wrap,
                UiTextAlign.LEFT,
                fontSize,
                fontFamily,
                preserveWhitespace,
                lineSpacing,
                spaceWidth,
            )
            return LayoutSize(knownWidth, layout.height)
        }
        val naturalWidth = buildLines(
            richText,
            Float.POSITIVE_INFINITY,
            wrap = false,
            fontSize,
            fontFamily,
            preserveWhitespace,
            lineSpacing,
            spaceWidth,
        )
            .maxOfOrNull { it.width } ?: 0f
        val width = knownWidth
            ?: availableWidth.takeIf { wrap && it > 0f }?.let { minOf(naturalWidth, it) }
            ?: naturalWidth
        val layout = layout(
            richText,
            width,
            Float.POSITIVE_INFINITY,
            wrap,
            UiTextAlign.LEFT,
            fontSize,
            fontFamily,
            preserveWhitespace,
            lineSpacing,
            spaceWidth,
        )
        val measuredWidth = if (wrap && width.isFinite()) {
            layout.lines.maxOfOrNull { it.naturalWidth } ?: width
        } else {
            width
        }
        return LayoutSize(measuredWidth, layout.height)
    }

    fun layout(
        text: String,
        width: Float,
        height: Float,
        wrap: Boolean,
        align: UiTextAlign,
        fontSize: Float,
        fontFamily: String? = null,
        preserveWhitespace: Boolean = false,
        lineSpacing: Float = 0f,
        spaceWidth: Float? = null,
    ): UiTextLayout = layout(
        UiRichText.plain(text),
        width,
        height,
        wrap,
        align,
        fontSize,
        fontFamily,
        preserveWhitespace,
        lineSpacing,
        spaceWidth,
    )

    fun layout(
        richText: UiRichText,
        width: Float,
        height: Float,
        wrap: Boolean,
        align: UiTextAlign,
        fontSize: Float,
        fontFamily: String? = null,
        preserveWhitespace: Boolean = false,
        lineSpacing: Float = 0f,
        spaceWidth: Float? = null,
    ): UiTextLayout {
        val resolvedLineSpacing = lineSpacing.coerceAtLeast(0f)
        val resolvedSpaceWidth = spaceWidth?.coerceAtLeast(0f)
        val key = LayoutCacheKey(
            richText,
            width.cacheValue(),
            height.cacheValue(),
            wrap,
            align,
            fontSize.cacheValue(),
            fontFamily,
            preserveWhitespace,
            resolvedLineSpacing.cacheValue(),
            resolvedSpaceWidth?.cacheValue(),
            fontSignature(fontFamily),
        )
        layoutCache[key]?.let { return it }
        val rawLines = buildLines(
            richText,
            width,
            wrap,
            fontSize,
            fontFamily,
            preserveWhitespace,
            resolvedLineSpacing,
            resolvedSpaceWidth,
        )
        val lines = mutableListOf<UiTextLine>()
        var y = 0f
        for (index in rawLines.indices) {
            val raw = rawLines[index]
            val lineAdvance = if (index == rawLines.lastIndex) raw.contentHeight else raw.advanceHeight
            if (height.isFinite() && lines.isNotEmpty() && y + lineAdvance > height) break
            val justify = align == UiTextAlign.JUSTIFY && !raw.lastInParagraph && raw.justifyGapCount > 0
            val textBoxWidth = raw.textBoxWidth.takeIf { it.isFinite() } ?: width
            val extra = if (justify) {
                (textBoxWidth - raw.width).coerceAtLeast(0f) / raw.justifyGapCount.coerceAtLeast(1)
            } else {
                0f
            }
            val x = if (justify) 0f else align.lineOffset(textBoxWidth, raw.width)
            lines += raw.toLine(x, y, textBoxWidth, justify, extra)
            y += lineAdvance
            if (height.isFinite() && y >= height) break
        }
        val layoutHeight = maxOf(
            y,
            lines.maxOfOrNull { line ->
                line.y + (line.fragments.maxOfOrNull { fragment -> fragment.y + fragment.height } ?: line.height)
            } ?: 0f,
        )
        return UiTextLayout(lines, width, layoutHeight).also { layoutCache[key] = it }
    }

    fun measureTextWidth(text: String, fontSize: Float, fontFamily: String? = null): Float {
        return measureTextWidth(text, fontSize, fontFamily, UiInlineStyle())
    }

    fun visibleTextPrefix(
        layout: UiTextLayout,
        visibleCharacters: Int,
        fontSize: Float,
        fontFamily: String? = null,
    ): UiTextLayout {
        var remaining = visibleCharacters.coerceAtLeast(0)
        val lines = layout.lines.mapNotNull { line ->
            val fragments = mutableListOf<UiTextFragment>()
            var lineText = StringBuilder()
            var consumedOnLine = 0
            fragmentLoop@ for (fragment in line.fragments) {
                when (fragment) {
                    is UiInlineImageRun -> fragments += fragment
                    is UiInlineWidgetRun -> fragments += fragment
                    is UiTextSpaceRun -> {
                        if (remaining <= 0) break@fragmentLoop
                        fragments += fragment
                        lineText.append(' ')
                        remaining -= 1
                        consumedOnLine += 1
                    }

                    is UiTextRun -> {
                        if (remaining <= 0) break@fragmentLoop
                        val visible = fragment.text.take(remaining)
                        if (visible.isEmpty()) continue@fragmentLoop
                        val size = fragment.style.resolvedFontSize(fontSize)
                        fragments += fragment.copy(
                            text = visible,
                            width = measureTextWidth(visible, size, fontFamily, fragment.style),
                        )
                        lineText.append(visible)
                        remaining -= visible.length
                        consumedOnLine += visible.length
                        if (visible.length < fragment.text.length) break@fragmentLoop
                    }
                }
            }
            remaining -= (line.sourceLength - consumedOnLine).coerceAtLeast(0)
            if (fragments.isEmpty()) return@mapNotNull null
            line.copy(text = lineText.toString(), fragments = fragments)
        }
        val height = lines.lastOrNull()?.let { it.y + it.height } ?: 0f
        return layout.copy(lines = lines, height = height)
    }

    private fun measureTextWidth(text: String, fontSize: Float, fontFamily: String?, style: UiInlineStyle): Float {
        val resolvedFamily = style.fontFamily ?: fontFamily
        val key = TextWidthCacheKey(text, style, fontSize.cacheValue(), resolvedFamily, fontSignature(resolvedFamily))
        widthCache[key]?.let { return it }
        val width = UiTextFonts.resolve(resolvedFamily).width(text, fontSize, style)
        return width.also { widthCache[key] = it }
    }

    private fun buildLines(
        richText: UiRichText,
        width: Float,
        wrap: Boolean,
        fontSize: Float,
        fontFamily: String?,
        preserveWhitespace: Boolean,
        lineSpacing: Float,
        spaceWidth: Float?,
    ): List<RawTextLine> {
        val key = LineCacheKey(
            richText,
            width.cacheValue(),
            wrap,
            fontSize.cacheValue(),
            fontFamily,
            preserveWhitespace,
            lineSpacing.cacheValue(),
            spaceWidth?.cacheValue(),
            fontSignature(fontFamily),
        )
        lineCache[key]?.let { return it }
        val units = richText.toUnits(fontSize, fontFamily, spaceWidth)
        val result = mutableListOf<RawTextLine>()
        val current = mutableListOf<InlineUnit>()
        val floating = mutableListOf<FloatingWidget>()
        val pendingFloatingFragments = mutableListOf<FloatingWidgetFragment>()
        var currentWidth = 0f
        var pendingSpace: InlineUnit.Space? = null
        var endedWithNewline = false
        var y = 0f

        fun commit(lastInParagraph: Boolean, trailingSourceCharacters: Int = 0) {
            val bounds = lineBounds(width, y, floating)
            val line = RawTextLine(
                current.toList(),
                lastInParagraph,
                trailingSourceCharacters,
                bounds.x,
                bounds.width,
                pendingFloatingFragments.toList(),
                lineSpacing,
            )
            result += line
            y += line.advanceHeight
            current.clear()
            currentWidth = 0f
            pendingSpace = null
            pendingFloatingFragments.clear()
        }

        unitLoop@ for (unit in units) {
            val bounds = lineBounds(width, y, floating)
            when (unit) {
                InlineUnit.Newline -> {
                    commit(lastInParagraph = true, trailingSourceCharacters = 1)
                    endedWithNewline = true
                }

                is InlineUnit.Space -> {
                    endedWithNewline = false
                    if (preserveWhitespace) {
                        if (wrap && current.isNotEmpty() && currentWidth + unit.width > bounds.width) {
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
                    val availableWidth = bounds.width.takeIf { it > 0f } ?: width
                    if (wrap && availableWidth.isFinite() && availableWidth > 0f && unit.width > availableWidth) {
                        if (current.isNotEmpty()) {
                            commit(lastInParagraph = false, trailingSourceCharacters = if (space == null) 0 else 1)
                        }
                        val chunks = splitOversizedWord(unit, availableWidth, fontFamily)
                        chunks.forEachIndexed { index, chunk ->
                            current += chunk
                            currentWidth = chunk.width
                            if (index < chunks.lastIndex) commit(lastInParagraph = false)
                        }
                        pendingSpace = null
                        continue@unitLoop
                    }

                    val candidateWidth = currentWidth + (space?.width ?: 0f) + unit.width
                    if (wrap && current.isNotEmpty() && candidateWidth > bounds.width) {
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
                    if (wrap && current.isNotEmpty() && candidateWidth > bounds.width) {
                        commit(lastInParagraph = false, trailingSourceCharacters = if (space == null) 0 else 1)
                    } else if (space != null) {
                        current += space
                        currentWidth += space.width
                    }
                    current += unit
                    currentWidth += unit.width
                    pendingSpace = null
                }

                is InlineUnit.Widget -> {
                    endedWithNewline = false
                    if (unit.widget.flow != UiTextWidgetFlow.INLINE) {
                        if (current.isNotEmpty()) commit(lastInParagraph = false)
                        val updatedBounds = lineBounds(width, y, floating)
                        val x = when (unit.widget.flow) {
                            UiTextWidgetFlow.FLOAT_END -> (width - unit.width).coerceAtLeast(0f)
                            else -> updatedBounds.x
                        }
                        floating += FloatingWidget(unit.widget, x, y, unit.width, unit.height, FloatingWidgetGap)
                        pendingFloatingFragments += FloatingWidgetFragment(unit.widget, x, 0f, unit.width, unit.height)
                        pendingSpace = null
                        continue@unitLoop
                    }
                    val space = pendingSpace
                    val candidateWidth = currentWidth + (space?.width ?: 0f) + unit.width
                    if (wrap && current.isNotEmpty() && candidateWidth > bounds.width) {
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
        if (current.isNotEmpty() || pendingFloatingFragments.isNotEmpty() || result.isEmpty() || endedWithNewline) {
            commit(lastInParagraph = true)
        }
        return result.also { lineCache[key] = it }
    }

    private fun fontSignature(fontFamily: String?): Int = UiTextFonts.signature(fontFamily)

    private fun splitOversizedWord(word: InlineUnit.Word, width: Float, fontFamily: String?): List<InlineUnit.Word> {
        val chunks = mutableListOf<InlineUnit.Word>()
        val buffer = StringBuilder()

        fun flush() {
            if (buffer.isEmpty()) return
            val text = buffer.toString()
            chunks += InlineUnit.Word(text, word.style, measureTextWidth(text, word.height, fontFamily, word.style), word.height)
            buffer.setLength(0)
        }

        for (char in word.text) {
            val candidate = buffer.toString() + char
            val candidateWidth = measureTextWidth(candidate, word.height, fontFamily, word.style)
            if (buffer.isNotEmpty() && candidateWidth > width) flush()
            buffer.append(char)
            if (measureTextWidth(buffer.toString(), word.height, fontFamily, word.style) > width) flush()
        }
        flush()
        return chunks.ifEmpty { listOf(word) }
    }

    private fun UiRichText.toUnits(baseFontSize: Float, fontFamily: String?, spaceWidth: Float?): List<InlineUnit> {
        val units = mutableListOf<InlineUnit>()
        for (item in items) {
            when (item) {
                is UiInlineItem.Image -> units += InlineUnit.Image(item)
                is UiInlineItem.Widget -> units += InlineUnit.Widget(item)
                is UiInlineItem.Text -> item.value.toUnits(item.style, baseFontSize, fontFamily, spaceWidth, units)
            }
        }
        return units
    }

    private fun String.toUnits(
        style: UiInlineStyle,
        baseFontSize: Float,
        fontFamily: String?,
        spaceWidth: Float?,
        target: MutableList<InlineUnit>,
    ) {
        val buffer = StringBuilder()
        fun flushWord() {
            if (buffer.isEmpty()) return
            val text = buffer.toString()
            val size = style.resolvedFontSize(baseFontSize)
            val resolvedFamily = style.fontFamily ?: fontFamily
            val lineHeight = UiTextFonts.resolve(resolvedFamily).lineHeight(size)
            target += InlineUnit.Word(text, style, measureTextWidth(text, size, fontFamily, style), lineHeight)
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
                    val resolvedFamily = style.fontFamily ?: fontFamily
                    val lineHeight = UiTextFonts.resolve(resolvedFamily).lineHeight(size)
                    target += InlineUnit.Space(style, spaceWidth ?: measureTextWidth(" ", size, fontFamily, style), lineHeight)
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
        var x = 0f
        val fragments = mutableListOf<UiTextFragment>()
        val finalLineX = lineX + textX
        for (floating in floatingFragments) {
            fragments += UiInlineWidgetRun(
                floating.widget,
                floating.x - finalLineX,
                floating.y,
                floating.width,
                floating.height,
            )
        }
        for (unit in units) {
            when (unit) {
                InlineUnit.Newline -> Unit
                is InlineUnit.Space -> {
                    val spaceWidth = unit.width + if (justify) extraSpace else 0f
                    fragments += UiTextSpaceRun(unit.style, x, unit.y, spaceWidth, unit.height)
                    x += spaceWidth
                }

                is InlineUnit.Word -> {
                    fragments += UiTextRun(unit.text, unit.style, x, unit.y, unit.width, unit.height)
                    x += unit.width
                }

                is InlineUnit.Image -> {
                    fragments += UiInlineImageRun(unit.image, x, unit.y, unit.width, unit.height)
                    x += unit.width
                }

                is InlineUnit.Widget -> {
                    fragments += UiInlineWidgetRun(unit.widget, x, unit.y, unit.width, unit.height)
                    x += unit.width
                }
            }
        }
        return UiTextLine(
            text = text,
            x = finalLineX,
            y = lineY,
            width = if (justify) textBoxWidth else width,
            naturalWidth = naturalWidth,
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

    private fun lineBounds(width: Float, y: Float, floating: List<FloatingWidget>): LineBounds {
        if (!width.isFinite() || floating.isEmpty()) return LineBounds(0f, width)
        var start = 0f
        var end = width
        for (widget in floating) {
            if (y < widget.y || y >= widget.y + widget.height) continue
            when (widget.widget.flow) {
                UiTextWidgetFlow.FLOAT_START -> start = maxOf(start, widget.x + widget.width + widget.gap)
                UiTextWidgetFlow.FLOAT_END -> end = minOf(end, widget.x - widget.gap)
                UiTextWidgetFlow.INLINE -> Unit
            }
        }
        return LineBounds(start, (end - start).coerceAtLeast(0f))
    }

    private data class LineBounds(
        val x: Float,
        val width: Float,
    )

    private data class FloatingWidget(
        val widget: UiInlineItem.Widget,
        val x: Float,
        val y: Float,
        val width: Float,
        val height: Float,
        val gap: Float,
    )

    private data class FloatingWidgetFragment(
        val widget: UiInlineItem.Widget,
        val x: Float,
        val y: Float,
        val width: Float,
        val height: Float,
    )

    private data class RawTextLine(
        val units: List<InlineUnit>,
        val lastInParagraph: Boolean,
        val trailingSourceCharacters: Int = 0,
        val textX: Float = 0f,
        val textBoxWidth: Float = Float.POSITIVE_INFINITY,
        val floatingFragments: List<FloatingWidgetFragment> = emptyList(),
        val lineSpacing: Float = 0f,
    ) {
        val text: String = buildString {
            for (unit in units) {
                when (unit) {
                    InlineUnit.Newline -> Unit
                    is InlineUnit.Space -> append(' ')
                    is InlineUnit.Word -> append(unit.text)
                    is InlineUnit.Image -> append(unit.image.alt.ifBlank { "\uFFFC" })
                    is InlineUnit.Widget -> append(unit.widget.alt.ifBlank { "\uFFFC" })
                }
            }
        }
        val width: Float = units.sumOf { it.width.toDouble() }.toFloat()
        val naturalWidth: Float = maxOf(
            textX + width,
            floatingFragments.maxOfOrNull { it.x + it.width } ?: 0f,
        )
        val height: Float
        val contentHeight: Float
        val advanceHeight: Float
        val sourceLength: Int = text.length + trailingSourceCharacters
        val justifyGapCount: Int = units.count { it is InlineUnit.Space }

        init {
            val textHeight = units.filterIsInstance<InlineUnit.Word>().maxOfOrNull { it.height } ?: DefaultUiFontSize
            val lineHeight = units.maxOfOrNull { it.height }
                ?: floatingFragments.maxOfOrNull { it.y + it.height }
                ?: textHeight
            val imageBaseline = units.filterIsInstance<InlineUnit.Image>().maxOfOrNull { image ->
                if (image.image.align == UiInlineAlign.BASELINE) image.height else 0f
            } ?: 0f
            val widgetBaseline = units.filterIsInstance<InlineUnit.Widget>().maxOfOrNull { widget ->
                if (widget.widget.align == UiInlineAlign.BASELINE) widget.height else 0f
            } ?: 0f
            val baseline = maxOf(textHeight, imageBaseline, widgetBaseline)
            units.forEach { it.resolveY(lineHeight, baseline) }
            val lineBottom = units.maxOfOrNull { it.y + it.height }
                ?: floatingFragments.maxOfOrNull { it.y + it.height }
                ?: lineHeight
            contentHeight = maxOf(
                lineHeight,
                lineBottom,
            )
            height = contentHeight
            advanceHeight = contentHeight + lineSpacing
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

        data class Widget(
            val widget: UiInlineItem.Widget,
        ) : InlineUnit {
            override val width: Float = widget.width
            override val height: Float = widget.height
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
            is InlineUnit.Widget -> when (widget.align) {
                UiInlineAlign.BASELINE -> baseline - height
                UiInlineAlign.MIDDLE -> (lineHeight - height) / 2f
                UiInlineAlign.TOP -> 0f
                UiInlineAlign.BOTTOM -> lineHeight - height
            }
        }.coerceAtLeast(0f)
    }

    private data class LineCacheKey(
        val richText: UiRichText,
        val width: Float,
        val wrap: Boolean,
        val fontSize: Float,
        val fontFamily: String?,
        val preserveWhitespace: Boolean,
        val lineSpacing: Float,
        val spaceWidth: Float?,
        val fontSignature: Int,
    )

    private data class LayoutCacheKey(
        val richText: UiRichText,
        val width: Float,
        val height: Float,
        val wrap: Boolean,
        val align: UiTextAlign,
        val fontSize: Float,
        val fontFamily: String?,
        val preserveWhitespace: Boolean,
        val lineSpacing: Float,
        val spaceWidth: Float?,
        val fontSignature: Int,
    )

    private data class TextWidthCacheKey(
        val text: String,
        val style: UiInlineStyle,
        val fontSize: Float,
        val fontFamily: String?,
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

fun UiTextLayout.caretPosition(index: Int, fontSize: Float, fontFamily: String? = null): UiVec3 {
    if (lines.isEmpty()) return UiVec3()
    var consumed = 0
    for (line in lines) {
        val lineEnd = consumed + line.sourceLength
        if (index < lineEnd || line === lines.last()) {
            val local = (index - consumed).coerceIn(0, line.sourceLength)
            return UiVec3(line.xAt(local, fontSize, fontFamily), line.y)
        }
        consumed = lineEnd
    }
    val last = lines.last()
    return UiVec3(last.xAt(last.sourceLength, fontSize, fontFamily), last.y)
}

fun UiTextLayout.caretIndexAt(x: Float, y: Float, fontSize: Float, fontFamily: String? = null): Int {
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
        val distance = abs(line.xAt(offset, fontSize, fontFamily) - x)
        if (distance < bestDistance) {
            bestDistance = distance
            bestOffset = offset
        }
    }
    return lines.takeWhile { it !== line }.sumOf { it.sourceLength } + bestOffset
}

fun UiTextLayout.selectionRects(start: Int, end: Int, fontSize: Float, fontFamily: String? = null): List<UiRect> {
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
            val x1 = line.xAt(startOffset, fontSize, fontFamily)
            val x2 = line.xAt(endOffset, fontSize, fontFamily)
            rects += UiRect(minOf(x1, x2), line.y, abs(x2 - x1), line.height)
        }
        consumed = lineEnd
    }
    return rects
}

private fun UiTextLine.xAt(offset: Int, fontSize: Float, fontFamily: String?): Float {
    val textOffset = offset.coerceIn(0, text.length)
    if (fragments.isEmpty()) return x + UiTextLayouter.measureTextWidth(text.take(textOffset), fontSize, fontFamily)

    var remaining = textOffset
    var cursor = 0f
    for (fragment in fragments) {
        when (fragment) {
            is UiInlineImageRun -> {
                val imageTextLength = fragment.image.alt.ifBlank { "\uFFFC" }.length
                if (remaining <= imageTextLength) return x + cursor
                remaining -= imageTextLength
                cursor = fragment.x + fragment.width
            }

            is UiInlineWidgetRun -> {
                val widgetTextLength = fragment.widget.alt.ifBlank { "\uFFFC" }.length
                if (remaining <= widgetTextLength) return x + cursor
                remaining -= widgetTextLength
                cursor = fragment.x + fragment.width
            }

            is UiTextSpaceRun -> {
                if (remaining <= 1) return x + fragment.x + if (remaining == 0) 0f else fragment.width
                remaining -= 1
                cursor = fragment.x + fragment.width
            }

            is UiTextRun -> {
                if (remaining <= fragment.text.length) {
                    val size = fragment.style.resolvedFontSize(fontSize)
                    return x + fragment.x + UiTextLayouter.measureTextWidth(
                        fragment.text.take(remaining),
                        size,
                        fontFamily,
                    )
                }
                remaining -= fragment.text.length
                cursor = fragment.x + fragment.width
            }
        }
    }
    return x + cursor
}
