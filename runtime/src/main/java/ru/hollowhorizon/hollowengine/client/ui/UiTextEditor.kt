package ru.hollowhorizon.hollowengine.client.ui

data class UiTextCaret(
    val position: Int,
    val selectionAnchor: Int? = null,
) {
    val selectionStart: Int get() = minOf(position, selectionAnchor ?: position)
    val selectionEnd: Int get() = maxOf(position, selectionAnchor ?: position)
    val hasSelection: Boolean get() = selectionStart != selectionEnd

    fun coerceIn(length: Int): UiTextCaret {
        return UiTextCaret(
            position = position.coerceIn(0, length),
            selectionAnchor = selectionAnchor?.coerceIn(0, length),
        )
    }
}

data class UiTextHighlight(
    val start: Int,
    val end: Int,
    val style: UiInlineStyle,
)

fun interface UiSyntaxHighlighter {
    fun highlight(text: String): List<UiTextHighlight>
}

internal fun String.toHighlightedRichText(highlighter: UiSyntaxHighlighter?): UiRichText {
    if (isEmpty() || highlighter == null) return UiRichText.plain(this)
    val highlights = highlighter.highlight(this)
        .mapNotNull { highlight ->
            val start = highlight.start.coerceIn(0, length)
            val end = highlight.end.coerceIn(start, length)
            if (start == end) null else highlight.copy(start = start, end = end)
        }
        .sortedWith(compareBy<UiTextHighlight> { it.start }.thenBy { it.end })
    if (highlights.isEmpty()) return UiRichText.plain(this)

    val items = mutableListOf<UiInlineItem>()
    var index = 0
    for (highlight in highlights) {
        if (highlight.start < index) continue
        if (index < highlight.start) {
            items += UiInlineItem.Text(substring(index, highlight.start))
        }
        items += UiInlineItem.Text(substring(highlight.start, highlight.end), highlight.style)
        index = highlight.end
    }
    if (index < length) {
        items += UiInlineItem.Text(substring(index))
    }
    return UiRichText(items)
}
