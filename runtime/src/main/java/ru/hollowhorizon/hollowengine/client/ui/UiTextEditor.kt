package ru.hollowhorizon.hollowengine.client.ui

import java.util.LinkedHashMap

internal const val InlayHintVisualOffsetX = 2f
private const val HighlightedRichTextCacheSize = 24

private val highlightedRichTextCache = object : LinkedHashMap<HighlightedRichTextCacheKey, UiRichText>(
    HighlightedRichTextCacheSize,
    0.75f,
    true,
) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<HighlightedRichTextCacheKey, UiRichText>): Boolean {
        return size > HighlightedRichTextCacheSize
    }
}

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

data class UiCompletionContext(
    val text: String,
    val caret: Int,
)

data class UiTextCompletion(
    val label: String,
    val insertText: String = label,
    val detail: String = "",
    val caretOffset: Int? = null,
)

fun interface UiCompletionContributor {
    fun complete(context: UiCompletionContext): List<UiTextCompletion>
}

enum class UiTextDiagnosticSeverity {
    ERROR,
    WARNING,
    INFO,
}

data class UiTextDiagnostic(
    val start: Int,
    val end: Int,
    val message: String,
    val severity: UiTextDiagnosticSeverity = UiTextDiagnosticSeverity.ERROR,
)

data class UiInlayHint(
    val offset: Int,
    val text: String,
)

fun interface UiInlayHintsProvider {
    fun hints(text: String): List<UiInlayHint>
}

internal fun String.toHighlightedRichText(
    highlighter: UiSyntaxHighlighter?,
    inlayHints: List<UiInlayHint> = emptyList(),
    inlayStyle: UiInlineStyle = UiInlineStyle(),
): UiRichText {
    if (isEmpty()) return UiRichText.plain(this)
    if (highlighter == null && inlayHints.isEmpty()) return UiRichText.plain(this)
    val cacheKey = HighlightedRichTextCacheKey(
        text = this,
        highlighter = highlighter,
        inlayHints = inlayHints.toList(),
        inlayStyle = inlayStyle,
    )
    highlightedRichTextCache[cacheKey]?.let { return it }
    val highlights = highlighter?.highlight(this).orEmpty()
        .mapNotNull { highlight ->
            val start = highlight.start.coerceIn(0, length)
            val end = highlight.end.coerceIn(start, length)
            if (start == end) null else highlight.copy(start = start, end = end)
        }
        .sortedWith(compareBy<UiTextHighlight> { it.start }.thenBy { it.end })
    val inlaysByOffset = inlayHints
        .filter { it.text.isNotBlank() }
        .groupBy { it.offset.coerceIn(0, length) }
    if (highlights.isEmpty() && inlaysByOffset.isEmpty()) return UiRichText.plain(this)

    val segments = mutableListOf<TextStyleSpan>()
    var index = 0
    for (highlight in highlights) {
        if (highlight.start < index) continue
        if (index < highlight.start) {
            segments += TextStyleSpan(index, highlight.start, UiInlineStyle())
        }
        segments += TextStyleSpan(highlight.start, highlight.end, highlight.style)
        index = highlight.end
    }
    if (index < length) {
        segments += TextStyleSpan(index, length, UiInlineStyle())
    }
    if (segments.isEmpty()) segments += TextStyleSpan(0, length, UiInlineStyle())

    val items = mutableListOf<UiInlineItem>()
    val emittedInlays = mutableSetOf<Int>()
    fun emitInlays(offset: Int) {
        if (!emittedInlays.add(offset)) return
        inlaysByOffset[offset].orEmpty().forEach { hint ->
            items += UiInlineItem.Inlay(hint.text, inlayStyle)
        }
    }
    for (segment in segments) {
        var cursor = segment.start
        emitInlays(cursor)
        while (cursor < segment.end) {
            val nextInlay = inlaysByOffset.keys
                .filter { it > cursor && it <= segment.end }
                .minOrNull()
                ?: segment.end
            if (cursor < nextInlay) {
                items += UiInlineItem.Text(substring(cursor, nextInlay), segment.style)
            }
            cursor = nextInlay
            emitInlays(cursor)
        }
    }
    emitInlays(length)
    return UiRichText(items).also { highlightedRichTextCache[cacheKey] = it }
}

private data class HighlightedRichTextCacheKey(
    val text: String,
    val highlighter: UiSyntaxHighlighter?,
    val inlayHints: List<UiInlayHint>,
    val inlayStyle: UiInlineStyle,
)

private data class TextStyleSpan(
    val start: Int,
    val end: Int,
    val style: UiInlineStyle,
)
