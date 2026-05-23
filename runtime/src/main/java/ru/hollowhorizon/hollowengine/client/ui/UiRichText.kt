package ru.hollowhorizon.hollowengine.client.ui

data class UiRichText(
    val items: List<UiInlineItem>,
) {
    companion object {
        fun plain(text: String): UiRichText = UiRichText(listOf(UiInlineItem.Text(text)))
    }
}

sealed interface UiInlineItem {
    data class Text(
        val value: String,
        val style: UiInlineStyle = UiInlineStyle(),
    ) : UiInlineItem

    data class Image(
        val source: String,
        val width: Float,
        val height: Float,
        val align: UiInlineAlign = UiInlineAlign.BASELINE,
        val alt: String = "",
    ) : UiInlineItem
}

data class UiInlineStyle(
    val color: UiColor? = null,
    val fontSize: Float? = null,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strikethrough: Boolean = false,
    val code: Boolean = false,
    val link: String? = null,
) {
    fun merge(other: UiInlineStyle): UiInlineStyle = UiInlineStyle(
        color = other.color ?: color,
        fontSize = other.fontSize ?: fontSize,
        bold = bold || other.bold,
        italic = italic || other.italic,
        underline = underline || other.underline,
        strikethrough = strikethrough || other.strikethrough,
        code = code || other.code,
        link = other.link ?: link,
    )

    fun resolvedFontSize(baseFontSize: Float): Float = (fontSize ?: baseFontSize).coerceAtLeast(0.0001f)
}

enum class UiInlineAlign {
    BASELINE,
    MIDDLE,
    TOP,
    BOTTOM
}

object UiRichTextParser {
    fun parse(source: String): UiRichText {
        return UiRichText(parseRange(source, UiInlineStyle()))
    }

    private fun parseRange(source: String, style: UiInlineStyle): List<UiInlineItem> {
        val items = mutableListOf<UiInlineItem>()
        val buffer = StringBuilder()
        var index = 0
        fun flushText() {
            if (buffer.isNotEmpty()) {
                items += UiInlineItem.Text(buffer.toString(), style)
                buffer.setLength(0)
            }
        }

        while (index < source.length) {
            parseImage(source, index)?.let { image ->
                flushText()
                items += image.item
                index = image.nextIndex
                continue
            }
            parseDelimited(source, index, "**", style.copy(bold = true))?.let { parsed ->
                flushText()
                items += parsed.items
                index = parsed.nextIndex
                continue
            }
            parseDelimited(source, index, "~~", style.copy(strikethrough = true))?.let { parsed ->
                flushText()
                items += parsed.items
                index = parsed.nextIndex
                continue
            }
            parseDelimited(source, index, "`", style.copy(code = true))?.let { parsed ->
                flushText()
                items += parsed.items
                index = parsed.nextIndex
                continue
            }
            parseDelimited(source, index, "*", style.copy(italic = true))?.let { parsed ->
                flushText()
                items += parsed.items
                index = parsed.nextIndex
                continue
            }
            parseLink(source, index, style)?.let { parsed ->
                flushText()
                items += parsed.items
                index = parsed.nextIndex
                continue
            }
            parseTag(source, index, style)?.let { parsed ->
                flushText()
                items += parsed.items
                index = parsed.nextIndex
                continue
            }
            buffer.append(source[index])
            index++
        }
        flushText()
        return items
    }

    private fun parseDelimited(
        source: String,
        index: Int,
        delimiter: String,
        style: UiInlineStyle,
    ): ParsedItems? {
        if (!source.startsWith(delimiter, index)) return null
        val end = source.indexOf(delimiter, index + delimiter.length)
        if (end < 0) return null
        val inner = source.substring(index + delimiter.length, end)
        return ParsedItems(parseRange(inner, style), end + delimiter.length)
    }

    private fun parseLink(source: String, index: Int, style: UiInlineStyle): ParsedItems? {
        if (!source.startsWith("[", index)) return null
        val labelEnd = source.indexOf("](", index + 1)
        if (labelEnd < 0) return null
        val targetEnd = source.indexOf(')', labelEnd + 2)
        if (targetEnd < 0) return null
        val link = source.substring(labelEnd + 2, targetEnd)
        val linkStyle = style.copy(link = link, underline = true)
        return ParsedItems(parseRange(source.substring(index + 1, labelEnd), linkStyle), targetEnd + 1)
    }

    private fun parseTag(source: String, index: Int, style: UiInlineStyle): ParsedItems? {
        if (!source.startsWith("<", index)) return null
        val tagEnd = source.indexOf('>', index + 1)
        if (tagEnd < 0) return null
        val tag = source.substring(index + 1, tagEnd).trim()
        val name = tag.substringBefore('=').lowercase()
        val close = "</$name>"
        val closeIndex = source.indexOf(close, tagEnd + 1, ignoreCase = true)
        if (closeIndex < 0) return null
        val tagStyle = when (name) {
            "b", "bold" -> style.copy(bold = true)
            "i", "italic" -> style.copy(italic = true)
            "u", "underline" -> style.copy(underline = true)
            "s", "strike", "strikethrough" -> style.copy(strikethrough = true)
            "code" -> style.copy(code = true)
            "color" -> parseTagValue(tag)?.let { style.copy(color = parseColor(it)) } ?: style
            "size" -> parseTagValue(tag)?.let { style.copy(fontSize = parseFontSize(it)) } ?: style
            else -> return null
        }
        return ParsedItems(parseRange(source.substring(tagEnd + 1, closeIndex), tagStyle), closeIndex + close.length)
    }

    private fun parseImage(source: String, index: Int): ParsedImage? {
        if (!source.startsWith("![", index)) return null
        val altEnd = source.indexOf("](", index + 2)
        if (altEnd < 0) return null
        val sourceEnd = source.indexOf(')', altEnd + 2)
        if (sourceEnd < 0) return null
        val dimensions = parseImageDimensions(source, sourceEnd + 1)
        val alt = source.substring(index + 2, altEnd)
        val imageSource = source.substring(altEnd + 2, sourceEnd)
        return ParsedImage(
            UiInlineItem.Image(
                source = imageSource,
                width = dimensions.width,
                height = dimensions.height,
                align = dimensions.align,
                alt = alt,
            ),
            dimensions.nextIndex,
        )
    }

    private fun parseImageDimensions(source: String, index: Int): ImageDimensions {
        if (index >= source.length || source[index] != '{') return ImageDimensions(16f, 16f, UiInlineAlign.BASELINE, index)
        val end = source.indexOf('}', index + 1)
        if (end < 0) return ImageDimensions(16f, 16f, UiInlineAlign.BASELINE, index)
        val parts = source.substring(index + 1, end).split(',').map { it.trim() }
        val size = parts.firstOrNull().orEmpty().split('x', 'X').map { it.trim() }
        val width = size.getOrNull(0)?.parsePx() ?: 16f
        val height = size.getOrNull(1)?.parsePx() ?: width
        val align = parts.getOrNull(1)?.let(::parseInlineAlign) ?: UiInlineAlign.BASELINE
        return ImageDimensions(width, height, align, end + 1)
    }

    private fun parseInlineAlign(value: String): UiInlineAlign = when (value.lowercase()) {
        "middle" -> UiInlineAlign.MIDDLE
        "top" -> UiInlineAlign.TOP
        "bottom" -> UiInlineAlign.BOTTOM
        else -> UiInlineAlign.BASELINE
    }

    private fun parseTagValue(tag: String): String? = tag.substringAfter('=', "").takeIf { it.isNotBlank() }
        ?.trim()
        ?.trim('"', '\'')

    private fun parseFontSize(value: String): Float = value.parsePx() ?: DefaultUiFontSize

    private fun parseColor(value: String): UiColor? {
        val text = value.trim().removePrefix("#")
        if (text.length != 6 && text.length != 8) return null
        val number = text.toLongOrNull(16) ?: return null
        val alpha = if (text.length == 8) (number and 0xFF).toFloat() / 255f else 1f
        val red = if (text.length == 8) (number shr 24) and 0xFF else (number shr 16) and 0xFF
        val green = if (text.length == 8) (number shr 16) and 0xFF else (number shr 8) and 0xFF
        val blue = if (text.length == 8) (number shr 8) and 0xFF else number and 0xFF
        return UiColor(red / 255f, green / 255f, blue / 255f, alpha)
    }

    private fun String.parsePx(): Float? = trim().removeSuffix("px").toFloatOrNull()

    private data class ParsedItems(
        val items: List<UiInlineItem>,
        val nextIndex: Int,
    )

    private data class ParsedImage(
        val item: UiInlineItem.Image,
        val nextIndex: Int,
    )

    private data class ImageDimensions(
        val width: Float,
        val height: Float,
        val align: UiInlineAlign,
        val nextIndex: Int,
    )
}
