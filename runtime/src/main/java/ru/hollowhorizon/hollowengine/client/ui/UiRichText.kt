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
