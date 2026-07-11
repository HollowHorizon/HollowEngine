package ru.hollowhorizon.hollowengine.client.ui.widgets

import androidx.compose.runtime.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun rememberEditableTextPresentation(
    text: String,
    caret: Int,
    highlighter: UiSyntaxHighlighter?,
    inlayHints: List<UiInlayHint>,
    inlayHintsProvider: UiInlayHintsProvider?,
    inlayRevision: Long,
): EditableTextPresentation {
    val cache = remember { EditableTextPresentationCache() }
    val safeCaret = normalizePresentationCaret(caret, text.length)
    val deferredAnalyzer = highlighter as? UiDeferredTextAnalyzer
    // Only caret-aware highlighters (occurrence brackets/identifiers) re-analyze on caret moves.
    val analysisCaretKey = when {
        deferredAnalyzer != null -> safeCaret
        highlighter is UiDeferredSyntaxHighlighter -> UiNoCaretOffset
        highlighter is UiCaretAwareSyntaxHighlighter -> safeCaret
        else -> UiNoCaretOffset
    }
    var completed by remember {
        mutableStateOf(
            value = cache.estimate(text, safeCaret, highlighter, inlayHints, inlayHintsProvider, inlayRevision),
            policy = referentialEqualityPolicy(),
        )
    }
    val immediate = cache.estimate(text, safeCaret, highlighter, inlayHints, inlayHintsProvider, inlayRevision)
    val visible = if (completed.matches(text, safeCaret, highlighter, inlayHintsProvider, inlayRevision)) completed else immediate

    LaunchedEffect(text, analysisCaretKey, highlighter, inlayHints, inlayHintsProvider, inlayRevision) {
        val analyzed = withContext(Dispatchers.Default) {
            val analysis = when {
                deferredAnalyzer != null -> deferredAnalyzer.deferredAnalysis(text, safeCaret) ?: return@withContext null
                highlighter == null -> UiTextAnalysis(emptyList(), inlayHints)
                highlighter is UiDeferredSyntaxHighlighter -> UiTextAnalysis(
                    highlights = highlighter.exactHighlight(text, UiNoCaretOffset) ?: return@withContext null,
                    inlayHints = inlayHintsProvider?.hints(text) ?: inlayHints,
                )
                highlighter is UiCaretAwareSyntaxHighlighter -> UiTextAnalysis(
                    highlights = highlighter.highlight(text, safeCaret),
                    inlayHints = inlayHintsProvider?.hints(text) ?: inlayHints,
                )
                else -> UiTextAnalysis(
                    highlights = highlighter.highlight(text),
                    inlayHints = inlayHintsProvider?.hints(text) ?: inlayHints,
                )
            }
            EditableTextPresentation.exact(
                text = text,
                caret = safeCaret,
                highlighter = highlighter,
                inlayHintsProvider = inlayHintsProvider,
                inlayRevision = inlayRevision,
                highlights = analysis.highlights,
                inlayHints = analysis.inlayHints,
            ) to analysis.exact
        } ?: return@LaunchedEffect
        val (presentation, exact) = analyzed
        if (exact) cache.accept(presentation) else cache.acceptEstimate(presentation)
        completed = presentation
    }

    return visible
}
internal class EditableTextPresentationCache {
    private var exact = EditableTextPresentation.Empty
    private var estimate = EditableTextPresentation.Empty

    fun estimate(
        text: String,
        caret: Int,
        highlighter: UiSyntaxHighlighter?,
        inlayHints: List<UiInlayHint>,
        inlayHintsProvider: UiInlayHintsProvider?,
        inlayRevision: Long,
    ): EditableTextPresentation {
        if (exact.matches(text, caret, highlighter, inlayHintsProvider, inlayRevision)) return exact
        if (exact.matchesAnalysis(text, highlighter, inlayHintsProvider, inlayRevision)) {
            return exact.withCaret(caret)
        }
        if (estimate.matches(text, caret, highlighter, inlayHintsProvider, inlayRevision)) return estimate
        if (estimate.matchesAnalysis(text, highlighter, inlayHintsProvider, inlayRevision)) {
            return estimate.withCaret(caret)
        }
        val next = if (highlighter == null && inlayHintsProvider == null) {
            EditableTextPresentation.exact(
                text = text,
                caret = caret,
                highlighter = highlighter,
                inlayHintsProvider = inlayHintsProvider,
                inlayRevision = inlayRevision,
                highlights = emptyList(),
                inlayHints = inlayHints,
            )
        } else {
            val base = estimate.takeIf { it.hasText } ?: exact
            base.shiftedTo(text, caret, highlighter, inlayHints, inlayHintsProvider, inlayRevision)
        }
        estimate = next
        if (highlighter == null && inlayHintsProvider == null) exact = next
        return next
    }

    fun accept(presentation: EditableTextPresentation) {
        exact = presentation
        estimate = presentation
    }

    fun acceptEstimate(presentation: EditableTextPresentation) {
        estimate = presentation
    }
}

internal data class EditableTextPresentation(
    val text: String,
    val textHash: Int,
    val textLength: Int,
    val caret: Int,
    val highlighter: UiSyntaxHighlighter?,
    val inlayHintsProvider: UiInlayHintsProvider?,
    val inlayRevision: Long,
    val highlights: List<UiTextHighlight>,
    val inlayHints: List<UiInlayHint>,
) {
    val hasText: Boolean get() = textLength >= 0

    fun matches(
        text: String,
        caret: Int,
        highlighter: UiSyntaxHighlighter?,
        inlayHintsProvider: UiInlayHintsProvider?,
        inlayRevision: Long,
    ): Boolean {
        return textHash == text.hashCode() &&
                textLength == text.length &&
                this.caret == normalizePresentationCaret(caret, text.length) &&
                this.highlighter === highlighter &&
                this.inlayHintsProvider === inlayHintsProvider &&
                this.inlayRevision == inlayRevision
    }

    fun matchesAnalysis(
        text: String,
        highlighter: UiSyntaxHighlighter?,
        inlayHintsProvider: UiInlayHintsProvider?,
        inlayRevision: Long,
    ): Boolean {
        return textHash == text.hashCode() &&
                textLength == text.length &&
                this.highlighter === highlighter &&
                this.inlayHintsProvider === inlayHintsProvider &&
                this.inlayRevision == inlayRevision
    }

    fun withCaret(nextCaret: Int): EditableTextPresentation {
        val safeCaret = normalizePresentationCaret(nextCaret, textLength.coerceAtLeast(0))
        return if (caret == safeCaret) this else copy(caret = safeCaret)
    }

    fun shiftedTo(
        nextText: String,
        nextCaret: Int,
        nextHighlighter: UiSyntaxHighlighter?,
        staticInlayHints: List<UiInlayHint>,
        nextInlayHintsProvider: UiInlayHintsProvider?,
        nextInlayRevision: Long,
    ): EditableTextPresentation {
        if (!hasText || highlighter !== nextHighlighter || inlayHintsProvider !== nextInlayHintsProvider) {
            return exact(
                text = nextText,
                caret = nextCaret,
                highlighter = nextHighlighter,
                inlayHintsProvider = nextInlayHintsProvider,
                inlayRevision = nextInlayRevision,
                highlights = emptyList(),
                inlayHints = if (nextInlayHintsProvider == null) staticInlayHints else emptyList(),
            )
        }
        val shiftedInlays = if (nextInlayHintsProvider == null) {
            staticInlayHints
        } else {
            shiftInlayHints(text, nextText, inlayHints)
        }
        return exact(
            text = nextText,
            caret = nextCaret,
            highlighter = nextHighlighter,
            inlayHintsProvider = nextInlayHintsProvider,
            inlayRevision = nextInlayRevision,
            highlights = shiftHighlights(text, nextText, highlights),
            inlayHints = shiftedInlays,
        )
    }

    companion object {
        val Empty = EditableTextPresentation("", 0, -1, 0, null, null, 0L, emptyList(), emptyList())

        fun exact(
            text: String,
            caret: Int,
            highlighter: UiSyntaxHighlighter?,
            inlayHintsProvider: UiInlayHintsProvider?,
            inlayRevision: Long,
            highlights: List<UiTextHighlight>,
            inlayHints: List<UiInlayHint>,
        ): EditableTextPresentation {
            return EditableTextPresentation(
                text = text,
                textHash = text.hashCode(),
                textLength = text.length,
                caret = normalizePresentationCaret(caret, text.length),
                highlighter = highlighter,
                inlayHintsProvider = inlayHintsProvider,
                inlayRevision = inlayRevision,
                highlights = sanitizeTextHighlights(text.length, highlights),
                inlayHints = sanitizeInlayHints(text.length, inlayHints),
            )
        }
    }
}

internal fun normalizePresentationCaret(caret: Int, textLength: Int): Int {
    return if (caret == UiNoCaretOffset) UiNoCaretOffset else caret.coerceIn(0, textLength)
}

private fun shiftHighlights(
    originalText: String,
    editedText: String,
    highlights: List<UiTextHighlight>,
): List<UiTextHighlight> {
    if (highlights.isEmpty()) return emptyList()
    val edit = commonEdit(originalText, editedText)
    return highlights.mapNotNull { highlight ->
        when {
            highlight.end <= edit.oldStart -> highlight
            highlight.start >= edit.oldEnd -> highlight.copy(
                start = (highlight.start + edit.delta).coerceIn(0, editedText.length),
                end = (highlight.end + edit.delta).coerceIn(0, editedText.length),
            )

            else -> null
        }
    }
}

private fun shiftInlayHints(
    originalText: String,
    editedText: String,
    hints: List<UiInlayHint>,
): List<UiInlayHint> {
    if (hints.isEmpty()) return emptyList()
    val edit = commonEdit(originalText, editedText)
    return hints.mapNotNull { hint ->
        when {
            hint.offset < edit.oldStart -> hint
            hint.offset >= edit.oldEnd -> hint.copy(offset = (hint.offset + edit.delta).coerceIn(0, editedText.length))
            else -> null
        }
    }
}

private data class EditableTextCommonEdit(
    val oldStart: Int,
    val oldEnd: Int,
    val delta: Int,
)

private fun commonEdit(left: String, right: String): EditableTextCommonEdit {
    val prefix = commonPrefixLength(left, right)
    val suffix = commonSuffixLength(left, right, prefix)
    return EditableTextCommonEdit(
        oldStart = prefix,
        oldEnd = left.length - suffix,
        delta = right.length - left.length,
    )
}

private fun commonPrefixLength(left: String, right: String): Int {
    val limit = minOf(left.length, right.length)
    for (index in 0 until limit) {
        if (left[index] != right[index]) return index
    }
    return limit
}

private fun commonSuffixLength(left: String, right: String, prefixLength: Int): Int {
    val limit = minOf(left.length, right.length) - prefixLength
    for (offset in 0 until limit) {
        if (left[left.lastIndex - offset] != right[right.lastIndex - offset]) return offset
    }
    return limit
}