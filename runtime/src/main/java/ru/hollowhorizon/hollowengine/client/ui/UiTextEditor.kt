package ru.hollowhorizon.hollowengine.client.ui

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

interface UiCaretAwareSyntaxHighlighter : UiSyntaxHighlighter {
    fun highlight(text: String, caret: Int): List<UiTextHighlight>

    override fun highlight(text: String): List<UiTextHighlight> = highlight(text, 0)
}

data class UiCompletionContext(
    val text: String,
    val caret: Int,
)

data class UiTextCompletion(
    val label: String,
    val insertText: String = label,
    val detail: String = "",
    val tail: String = "",
    val icon: String? = null,
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
    val line: Int = 0,
    val column: Int = 0,
)

data class UiInlayHint(
    val offset: Int,
    val text: String,
)

internal fun String.normalizeEditorLineEndings(): String {
    if ('\r' !in this) return this
    return replace("\r\n", "\n").replace('\r', '\n')
}

fun interface UiInlayHintsProvider {
    fun hints(text: String): List<UiInlayHint>
}

internal fun String.toHighlightedRichText(
    highlighter: UiSyntaxHighlighter?,
    inlayHints: List<UiInlayHint> = emptyList(),
    inlayStyle: UiInlineStyle = UiInlineStyle(),
    inlayWidgetMetrics: Map<String, UiInlineWidgetMetrics> = emptyMap(),
): UiRichText {
    if (isEmpty() || (highlighter == null && inlayHints.isEmpty())) {
        return UiRichText.plain(this)
    }

    val cacheKey = HighlightedRichTextCacheKey(this, highlighter, inlayHints.toList(), inlayStyle, inlayWidgetMetrics)
    highlightedRichTextCache[cacheKey]?.let { return it }

    val cleanHighlights = prepareHighlights(highlighter)
    val inlaysByOffset = inlayHints
        .filter { it.text.isNotBlank() }
        .groupBy { it.offset.coerceIn(0, length) }

    if (cleanHighlights.isEmpty() && inlaysByOffset.isEmpty()) {
        return UiRichText.plain(this)
    }

    val segments = buildTextSegments(cleanHighlights)
    val items = mergeTextWithInlays(segments, inlaysByOffset, inlayStyle, inlayWidgetMetrics)

    return UiRichText(items).also { highlightedRichTextCache[cacheKey] = it }
}

private fun String.prepareHighlights(highlighter: UiSyntaxHighlighter?): List<UiTextHighlight> {
    return highlighter?.highlight(this).orEmpty()
        .mapNotNull { highlight ->
            val start = highlight.start.coerceIn(0, length)
            val end = highlight.end.coerceIn(start, length)
            if (start == end) null else highlight.copy(start = start, end = end)
        }
        .sortedWith(compareBy<UiTextHighlight> { it.start }.thenBy { it.end })
}

private fun String.buildTextSegments(highlights: List<UiTextHighlight>): List<TextStyleSpan> {
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
    if (segments.isEmpty()) {
        segments += TextStyleSpan(0, length, UiInlineStyle())
    }
    return segments
}

private fun String.mergeTextWithInlays(
    segments: List<TextStyleSpan>,
    inlaysByOffset: Map<Int, List<UiInlayHint>>,
    inlayStyle: UiInlineStyle,
    inlayWidgetMetrics: Map<String, UiInlineWidgetMetrics>,
): List<UiInlineItem> {
    val items = mutableListOf<UiInlineItem>()
    val emittedInlays = mutableSetOf<Int>()
    var inlayIndex = 0

    fun emitInlaysAt(offset: Int) {
        if (emittedInlays.add(offset)) {
            inlaysByOffset[offset]?.forEach { hint ->
                val id = textFieldInlayWidgetId(hint, inlayIndex++)
                val metrics = inlayWidgetMetrics[id]
                items += UiInlineItem.Widget(
                    id = id,
                    width = metrics?.width ?: 0f,
                    height = metrics?.height ?: 0f,
                    align = UiInlineAlign.MIDDLE,
                    alt = "",
                    sourceLength = 0,
                )
            }
        }
    }

    val inlayOffsets = inlaysByOffset.keys

    for (segment in segments) {
        var cursor = segment.start
        emitInlaysAt(cursor)

        while (cursor < segment.end) {
            val nextInlay = inlayOffsets
                .filter { it > cursor && it <= segment.end }
                .minOrNull() ?: segment.end

            if (cursor < nextInlay) {
                items += UiInlineItem.Text(substring(cursor, nextInlay), segment.style)
            }
            cursor = nextInlay
            emitInlaysAt(cursor)
        }
    }
    emitInlaysAt(length)
    return items
}

private data class HighlightedRichTextCacheKey(
    val text: String,
    val highlighter: UiSyntaxHighlighter?,
    val inlayHints: List<UiInlayHint>,
    val inlayStyle: UiInlineStyle,
    val inlayWidgetMetrics: Map<String, UiInlineWidgetMetrics>,
)

internal fun textFieldInlayWidgetId(hint: UiInlayHint, index: Int): String {
    val hash = hint.text.hashCode().toUInt().toString(16)
    return "inlay-${hint.offset}-$index-$hash"
}

internal fun textFieldActiveInlayHints(
    text: String,
    inlayHints: List<UiInlayHint>,
    provider: UiInlayHintsProvider?,
): List<UiInlayHint> {
    val hints = provider?.hints(text) ?: inlayHints
    return hints
        .filter { it.text.isNotBlank() }
        .map { hint -> hint.copy(offset = hint.offset.coerceIn(0, text.length)) }
}

private data class TextStyleSpan(
    val start: Int,
    val end: Int,
    val style: UiInlineStyle,
)
