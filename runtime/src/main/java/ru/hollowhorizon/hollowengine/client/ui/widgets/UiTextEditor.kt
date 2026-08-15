package ru.hollowhorizon.hollowengine.client.ui.widgets

import ru.hollowhorizon.hollowengine.common.scripting.ide.TokenType

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
    val inlayAffinity: UiInlayCaretAffinity = UiInlayCaretAffinity.AFTER,
) {
    val selectionStart: Int get() = minOf(position, selectionAnchor ?: position)
    val selectionEnd: Int get() = maxOf(position, selectionAnchor ?: position)
    val hasSelection: Boolean get() = selectionStart != selectionEnd

    fun coerceIn(length: Int): UiTextCaret {
        return UiTextCaret(
            position = position.coerceIn(0, length),
            selectionAnchor = selectionAnchor?.coerceIn(0, length),
            inlayAffinity = inlayAffinity,
        )
    }
}

enum class UiInlayCaretAffinity {
    BEFORE,
    AFTER,
}

data class UiTextHighlight(
    val start: Int,
    val end: Int,
    val style: UiInlineStyle,
)

fun interface UiSyntaxHighlighter {
    fun highlight(text: String): List<UiTextHighlight>
}

fun interface UiTextPasteTransformer {
    fun transform(document: String, caret: UiTextCaret, pastedText: String): String
}

const val UiNoCaretOffset: Int = -1

interface UiCaretAwareSyntaxHighlighter : UiSyntaxHighlighter {
    fun highlight(text: String, caret: Int): List<UiTextHighlight>

    override fun highlight(text: String): List<UiTextHighlight> = highlight(text, UiNoCaretOffset)
}

interface UiDeferredSyntaxHighlighter : UiCaretAwareSyntaxHighlighter {
    fun exactHighlight(text: String, caret: Int): List<UiTextHighlight>?
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
    val importFqName: String? = null,
    val wordChars: String = "",
    /** Name used for matching when [label] also contains presentation text. */
    val filterText: String = label,
    /** Candidate ranges matched by the current prefix; populated by [TextFieldCompletionState]. */
    val matchRanges: List<IntRange> = emptyList(),
    /**
     * How close the declaration is to the caret; breaks ties between equally good matches, so a
     * local beats a class that only becomes available after an import. See
     * [ru.hollowhorizon.hollowengine.common.scripting.ide.CompletionCloseness].
     */
    val closeness: Int = 0,
)

fun interface UiCompletionContributor {
    fun complete(context: UiCompletionContext): List<UiTextCompletion>
}

data class UiTextSignature(
    val label: String,
    val parameters: List<IntRange>,
    val documentation: String? = null,
    val highlights: List<UiCodeInsightHighlight> = emptyList(),
    val presentation: IntRange = label.indices,
)

data class UiCodeInsightHighlight(
    val range: IntRange,
    val tokenType: TokenType,
)

data class UiTextSignatureHelp(
    val anchor: Int,
    val signatures: List<UiTextSignature>,
    val activeSignature: Int = 0,
    val activeParameter: Int = 0,
)

fun interface UiSignatureHelpProvider {
    fun help(context: UiCompletionContext): UiTextSignatureHelp?
}

data class UiTextHoverInfo(
    val start: Int,
    val end: Int,
    val signature: String,
    val documentation: String? = null,
    val highlights: List<UiCodeInsightHighlight> = emptyList(),
)

fun interface UiHoverInfoProvider {
    fun hover(context: UiCompletionContext): UiTextHoverInfo?
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

sealed interface UiInlayContent {
    data class Label(val text: String) : UiInlayContent

    data class Icon(val source: String) : UiInlayContent
}

/** Opaque identity of an inlay's action; the field hands it back to its host on click. */
@JvmInline
value class UiInlayAction(val id: String)

/**
 * A hint drawn between the glyphs at [offset]. [tags] reach the stylesheet as tags of the
 * inlay node. An [action] makes the hint clickable.
 */
data class UiInlayHint(
    val offset: Int,
    val content: List<UiInlayContent>,
    val tags: List<String> = emptyList(),
    val action: UiInlayAction? = null,
) {
    constructor(offset: Int, text: String, tags: List<String> = emptyList(), action: UiInlayAction? = null) :
            this(offset, listOf(UiInlayContent.Label(text)), tags, action)

    /** The hint's plain-text form; empty for hints drawn as icons only. */
    val text: String
        get() = content.filterIsInstance<UiInlayContent.Label>().joinToString("") { it.text }

    /** Identity of what this hint draws; equal content measures to the same size. */
    internal val contentKey: Int
        get() = 31 * content.hashCode() + tags.hashCode()
}

data class UiTextAnalysis(
    val highlights: List<UiTextHighlight>,
    val inlayHints: List<UiInlayHint> = emptyList(),
    val exact: Boolean = true,
)

internal fun String.normalizeEditorLineEndings(): String {
    if ('\r' !in this) return this
    return replace("\r\n", "\n").replace('\r', '\n')
}

internal data class UiTextImportResult(
    val text: String,
    val shiftBeforeReference: Int,
)

internal fun String.withSortedKotlinImport(fqName: String, referenceOffset: Int): UiTextImportResult {
    val cleanName = fqName.trim()
    if (cleanName.isEmpty()) return UiTextImportResult(this, 0)

    val importLine = "import $cleanName"
    val lines = split('\n')
    if (lines.any { it.trim() == importLine }) return UiTextImportResult(this, 0)

    val packageIndex = lines.indexOfFirst { it.trimStart().startsWith("package ") }
    val firstImportIndex = lines.indexOfFirst { it.trimStart().startsWith("import ") }
    val headerEnd = when {
        packageIndex >= 0 -> packageIndex + 1
        firstImportIndex >= 0 -> firstImportIndex
        else -> lines.kotlinFileHeaderEnd()
    }
    var importScan = headerEnd
    while (importScan < lines.size && lines[importScan].isBlank()) importScan++

    val importStart = importScan.takeIf { it < lines.size && lines[it].trimStart().startsWith("import ") } ?: importScan
    var importEnd = importStart
    while (importEnd < lines.size && lines[importEnd].trimStart().startsWith("import ")) importEnd++

    var bodyStart = importEnd
    while (bodyStart < lines.size && lines[bodyStart].isBlank()) bodyStart++

    val imports = (lines.subList(importStart, importEnd).map { it.trim() } + importLine)
        .distinct()
        .sorted()

    val rebuilt = buildList {
        addAll(lines.take(headerEnd).dropLastWhile(String::isBlank))
        if (isNotEmpty()) add("")
        addAll(imports)
        if (bodyStart < lines.size) {
            add("")
            addAll(lines.drop(bodyStart))
        }
    }.joinToString("\n")

    val reference = referenceOffset.coerceIn(0, length)
    val originalBodyOffset = lines.take(bodyStart).sumOf { it.length + 1 }.coerceAtMost(length)
    val shift = if (reference >= originalBodyOffset) rebuilt.length - length else 0
    return UiTextImportResult(rebuilt, shift)
}

private fun List<String>.kotlinFileHeaderEnd(): Int {
    var index = 0
    var inBlockComment = false

    fun skipTrivia() {
        while (index < size) {
            val trimmed = this[index].trim()
            if (inBlockComment) {
                inBlockComment = !trimmed.contains("*/")
                index++
            } else if (trimmed.isEmpty() || trimmed.startsWith("//")) {
                index++
            } else if (trimmed.startsWith("/*")) {
                inBlockComment = trimmed.indexOf("*/", startIndex = 2) < 0
                index++
            } else {
                break
            }
        }
    }

    skipTrivia()
    while (index < size && this[index].trimStart().startsWith("@file:")) {
        var depth = 0
        do {
            depth += this[index].annotationDelimiterDelta()
            index++
        } while (index < size && depth > 0)
        skipTrivia()
    }
    return index
}

private fun String.annotationDelimiterDelta(): Int {
    var delta = 0
    var quote: Char? = null
    var escaped = false
    for (char in this) {
        if (escaped) {
            escaped = false
            continue
        }
        if (char == '\\' && quote != null) {
            escaped = true
            continue
        }
        if (char == '\"' || char == '\'') {
            if (quote == char) quote = null else if (quote == null) quote = char
            continue
        }
        if (quote == null) {
            if (char == '(' || char == '[') delta++
            if (char == ')' || char == ']') delta--
        }
    }
    return delta
}

fun interface UiInlayHintsProvider {
    fun hints(text: String): List<UiInlayHint>
}

interface UiDeferredTextAnalyzer : UiDeferredSyntaxHighlighter, UiInlayHintsProvider {
    fun cachedAnalysis(text: String, caret: Int): UiTextAnalysis?

    fun deferredAnalysis(text: String, caret: Int): UiTextAnalysis? = cachedAnalysis(text, caret)

    override fun exactHighlight(text: String, caret: Int): List<UiTextHighlight>? {
        return cachedAnalysis(text, caret)?.highlights
    }

    override fun hints(text: String): List<UiInlayHint> {
        return cachedAnalysis(text, UiNoCaretOffset)?.inlayHints.orEmpty()
    }
}

internal fun String.toHighlightedRichText(
    highlighter: UiSyntaxHighlighter?,
    caret: Int = UiNoCaretOffset,
    inlayHints: List<UiInlayHint> = emptyList(),

    inlayWidgetMetrics: Map<String, UiInlineWidgetMetrics> = emptyMap(),
): UiRichText {
    if (inlayHints.isEmpty() && (isEmpty() || highlighter == null)) {
        return UiRichText.plain(this)
    }

    val cleanInlays = sanitizeInlayHints(length, inlayHints)
    val highlightCaret = if (highlighter is UiCaretAwareSyntaxHighlighter) caret else UiNoCaretOffset
    val cacheKey = HighlightedRichTextCacheKey(
        this, highlighter, highlightCaret, cleanInlays, inlayWidgetMetrics,
    )
    highlightedRichTextCache[cacheKey]?.let { return it }

    val cleanHighlights = prepareHighlights(highlighter, highlightCaret)
    val inlaysByOffset = cleanInlays.groupBy { it.offset }

    if (cleanHighlights.isEmpty() && inlaysByOffset.isEmpty()) {
        return UiRichText.plain(this)
    }

    val segments = buildTextSegments(cleanHighlights)
    val items = mergeTextWithInlays(segments, inlaysByOffset, inlayWidgetMetrics)

    return UiRichText(items).also { highlightedRichTextCache[cacheKey] = it }
}

internal fun String.toHighlightedRichText(
    highlights: List<UiTextHighlight>,
    inlayHints: List<UiInlayHint> = emptyList(),

    inlayWidgetMetrics: Map<String, UiInlineWidgetMetrics> = emptyMap(),
): UiRichText {
    if (inlayHints.isEmpty() && (isEmpty() || highlights.isEmpty())) {
        return UiRichText.plain(this)
    }

    val cleanHighlights = prepareHighlights(highlights)
    val cleanInlays = sanitizeInlayHints(length, inlayHints)
    val inlaysByOffset = cleanInlays.groupBy { it.offset }

    if (cleanHighlights.isEmpty() && inlaysByOffset.isEmpty()) {
        return UiRichText.plain(this)
    }

    val segments = buildTextSegments(cleanHighlights)
    val items = mergeTextWithInlays(segments, inlaysByOffset, inlayWidgetMetrics)
    return UiRichText(items)
}

private fun String.prepareHighlights(highlighter: UiSyntaxHighlighter?, caret: Int): List<UiTextHighlight> {
    val highlights = when (highlighter) {
        is UiCaretAwareSyntaxHighlighter -> highlighter.highlight(this, caret)
        null -> emptyList()
        else -> highlighter.highlight(this)
    }
    return prepareHighlights(highlights)
}

private fun String.prepareHighlights(highlights: List<UiTextHighlight>): List<UiTextHighlight> {
    return sanitizeTextHighlights(length, highlights)
}

private val UiTextHighlightOrder = compareBy<UiTextHighlight> { it.start }.thenBy { it.end }

internal fun sanitizeTextHighlights(textLength: Int, highlights: List<UiTextHighlight>): List<UiTextHighlight> {
    if (highlights.isEmpty()) return emptyList()

    var previousStart = -1
    var previousEnd = -1
    var needsCopy = false
    for (highlight in highlights) {
        val start = highlight.start.coerceIn(0, textLength)
        val end = highlight.end.coerceIn(start, textLength)
        if (start == end || start != highlight.start || end != highlight.end ||
            start < previousStart || start == previousStart && end < previousEnd
        ) {
            needsCopy = true
            break
        }
        previousStart = start
        previousEnd = end
    }
    if (!needsCopy) return highlights

    val clean = ArrayList<UiTextHighlight>(highlights.size)
    for (highlight in highlights) {
        val start = highlight.start.coerceIn(0, textLength)
        val end = highlight.end.coerceIn(start, textLength)
        if (start != end) {
            clean += if (start == highlight.start && end == highlight.end) {
                highlight
            } else {
                highlight.copy(start = start, end = end)
            }
        }
    }
    if (clean.isEmpty()) return emptyList()
    clean.sortWith(UiTextHighlightOrder)
    return clean
}

internal fun sanitizeInlayHints(textLength: Int, inlayHints: List<UiInlayHint>): List<UiInlayHint> {
    if (inlayHints.isEmpty()) return emptyList()

    var needsCopy = false
    for (hint in inlayHints) {
        val offset = hint.offset.coerceIn(0, textLength)
        if (hint.isEmpty() || offset != hint.offset) {
            needsCopy = true
            break
        }
    }
    if (!needsCopy) return inlayHints

    val clean = ArrayList<UiInlayHint>(inlayHints.size)
    for (hint in inlayHints) {
        if (hint.isEmpty()) continue
        val offset = hint.offset.coerceIn(0, textLength)
        clean += if (offset == hint.offset) hint else hint.copy(offset = offset)
    }
    return if (clean.isEmpty()) emptyList() else clean
}

/** A hint with nothing to draw: no parts, or only blank labels. */
private fun UiInlayHint.isEmpty(): Boolean = content.none { part ->
    when (part) {
        is UiInlayContent.Label -> part.text.isNotBlank()
        is UiInlayContent.Icon -> part.source.isNotBlank()
    }
}

private fun String.buildTextSegments(highlights: List<UiTextHighlight>): List<TextStyleSpan> {
    val segments = mutableListOf<TextStyleSpan>()
    var index = 0

    for (highlight in highlights) {
        if (highlight.start < index) continue
        if (index < highlight.start) {
            segments += TextStyleSpan(index, highlight.start, UiInlineStyle.Empty)
        }
        segments += TextStyleSpan(highlight.start, highlight.end, highlight.style)
        index = highlight.end
    }

    if (index < length) {
        segments += TextStyleSpan(index, length, UiInlineStyle.Empty)
    }
    if (segments.isEmpty()) {
        segments += TextStyleSpan(0, length, UiInlineStyle.Empty)
    }
    return segments
}

private fun String.mergeTextWithInlays(
    segments: List<TextStyleSpan>,
    inlaysByOffset: Map<Int, List<UiInlayHint>>,
    inlayWidgetMetrics: Map<String, UiInlineWidgetMetrics>,
): List<UiInlineItem> {
    val items = mutableListOf<UiInlineItem>()
    val inlayOffsets = inlaysByOffset.keys.sorted()
    var emittedOffset: Int? = null
    var inlayOffsetIndex = 0
    var inlayIndex = 0

    fun emitInlaysAt(offset: Int) {
        if (emittedOffset == offset) return
        emittedOffset = offset
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

    for (segment in segments) {
        var cursor = segment.start
        emitInlaysAt(cursor)

        while (cursor < segment.end) {
            while (inlayOffsetIndex < inlayOffsets.size && inlayOffsets[inlayOffsetIndex] <= cursor) {
                inlayOffsetIndex++
            }
            val nextInlay = inlayOffsets.getOrNull(inlayOffsetIndex)?.takeIf { it <= segment.end } ?: segment.end

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
    val caret: Int,
    val inlayHints: List<UiInlayHint>,

    val inlayWidgetMetrics: Map<String, UiInlineWidgetMetrics>,
)

internal fun textFieldInlayWidgetId(hint: UiInlayHint, index: Int): String {
    val hash = hint.contentKey.toUInt().toString(16)
    return "inlay-${hint.offset}-$index-$hash"
}

private data class TextStyleSpan(
    val start: Int,
    val end: Int,
    val style: UiInlineStyle,
)
