package ru.hollowhorizon.hollowengine.client.gui.scripting

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import net.minecraft.client.Minecraft
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.components.EditorLanguageService
import ru.hollowhorizon.hollowengine.client.ui.UiCaretAwareSyntaxHighlighter
import ru.hollowhorizon.hollowengine.client.ui.UiColor
import ru.hollowhorizon.hollowengine.client.ui.UiCompletionContext
import ru.hollowhorizon.hollowengine.client.ui.UiCompletionContributor
import ru.hollowhorizon.hollowengine.client.ui.UiInlayHint
import ru.hollowhorizon.hollowengine.client.ui.UiInlineStyle
import ru.hollowhorizon.hollowengine.client.ui.UiTextCompletion
import ru.hollowhorizon.hollowengine.client.ui.UiTextDiagnostic
import ru.hollowhorizon.hollowengine.client.ui.UiTextDiagnosticSeverity
import ru.hollowhorizon.hollowengine.client.ui.UiTextHighlight
import ru.hollowhorizon.hollowengine.client.ui.withBold
import ru.hollowhorizon.hollowengine.client.ui.withBackground
import ru.hollowhorizon.hollowengine.client.ui.withColor
import ru.hollowhorizon.hollowengine.client.ui.withItalic
import ru.hollowhorizon.hollowengine.common.scripting.ide.CompletionItem
import ru.hollowhorizon.hollowengine.common.scripting.ide.CompletionItemTag
import ru.hollowhorizon.hollowengine.common.scripting.ide.Diagnostic
import ru.hollowhorizon.hollowengine.common.scripting.ide.Severity
import ru.hollowhorizon.hollowengine.common.scripting.ide.SpanStyle
import ru.hollowhorizon.hollowengine.common.scripting.ide.TextLine
import ru.hollowhorizon.hollowengine.common.scripting.ide.TokenType
import ru.hollowhorizon.hollowengine.common.scripting.ide.UnavailableKotlinScriptingAnalyzer
import java.util.concurrent.atomic.AtomicLong

internal class HollowIdeEditorSession(
    private val path: String,
    private val onUpdated: () -> Unit,
) {
    private val analyzer = analyzerFor(path)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val analysisRevision = AtomicLong()
    private val completionRevision = AtomicLong()
    @Volatile
    private var requestedAnalysis: AnalysisKey? = null
    @Volatile
    private var requestedCompletion: CompletionKey? = null
    @Volatile
    private var analysisJob: Job? = null
    @Volatile
    private var completionJob: Job? = null
    @Volatile
    private var snapshot = EditorAnalysisSnapshot.Empty
    @Volatile
    private var completionSnapshot = CompletionSnapshot.Empty
    private val publishedRevision = AtomicLong()

    val revision: Long get() = publishedRevision.get()

    val highlighter: UiCaretAwareSyntaxHighlighter = object : UiCaretAwareSyntaxHighlighter {
        override fun highlight(text: String, caret: Int): List<UiTextHighlight> {
            requestAnalysis(text, caret)
            val current = snapshot
            return when {
                current.matches(text, caret) -> current.highlights
                current.matchesText(text) -> {
                    requestAnalysis(text, caret)
                    current.highlights
                }
                current.hasText -> current.highlightsForEditedText(text, ::lightweightHighlights)
                else -> lightweightHighlights(text)
            }
        }
    }

    val completions: UiCompletionContributor = UiCompletionContributor { context ->
        requestCompletions(context.text, context.caret)
        completionSnapshot.takeIf { it.matches(context.text, context.caret) }?.items.orEmpty()
    }

    fun inlayHints(text: String): List<UiInlayHint> {
        val current = snapshot
        if (!current.matchesText(text)) requestAnalysis(text, current.caret)
        val next = snapshot
        return when {
            next.matchesText(text) -> next.inlayHints
            next.hasText -> next.inlayHintsForEditedText(text)
            else -> emptyList()
        }
    }

    fun diagnostics(text: String): List<UiTextDiagnostic> {
        val current = snapshot
        if (!current.matchesText(text)) requestAnalysis(text, current.caret)
        return snapshot.takeIf { it.matchesText(text) }?.diagnostics.orEmpty()
    }

    fun requestAnalysis(text: String, caret: Int) {
        val key = AnalysisKey(text.hashCode(), text.length, caret.coerceIn(0, text.length))
        if (requestedAnalysis == key) return
        requestedAnalysis = key
        val requestRevision = analysisRevision.incrementAndGet()
        analysisJob?.cancel()
        analysisJob = scope.launch {
            val lineStarts = lineStarts(text)
            val lines = runCatching { analyzer.highlight(path, text, key.caret) }.getOrElse {
                UnavailableKotlinScriptingAnalyzer.highlight(path, text, key.caret)
            }
            val diagnostics = runCatching {
                analyzer.diagnostic(path, text).map { diagnostic -> diagnostic.toUi(text, lineStarts) }
            }.getOrDefault(emptyList())
            val next = EditorAnalysisSnapshot(
                text = text,
                textHash = key.textHash,
                textLength = key.textLength,
                caret = key.caret,
                highlights = lines.toHighlights(text, lineStarts),
                inlayHints = lines.toInlayHints(lineStarts, text.length),
                diagnostics = diagnostics,
            )
            publishAnalysisIfCurrent(requestRevision) {
                snapshot = next
            }
        }
    }

    private fun requestCompletions(text: String, caret: Int) {
        val key = CompletionKey(text.hashCode(), text.length, caret.coerceIn(0, text.length))
        if (requestedCompletion == key) return
        requestedCompletion = key
        val requestRevision = completionRevision.incrementAndGet()
        completionJob?.cancel()
        completionJob = scope.launch {
            val items = runCatching {
                analyzer.completions(path, text, key.caret)
                    .asSequence()
                    .map(CompletionItem::toUi)
                    .toList()
            }.getOrDefault(emptyList())
            publishCompletionIfCurrent(requestRevision) {
                completionSnapshot = CompletionSnapshot(key.textHash, key.textLength, key.caret, items)
            }
        }
    }

    private fun publishAnalysisIfCurrent(requestRevision: Long, update: () -> Unit) {
        if (requestRevision < analysisRevision.get()) return
        update()
        publishedRevision.incrementAndGet()
        Minecraft.getInstance().execute(onUpdated)
    }

    private fun publishCompletionIfCurrent(requestRevision: Long, update: () -> Unit) {
        if (requestRevision < completionRevision.get()) return
        update()
        publishedRevision.incrementAndGet()
        Minecraft.getInstance().execute(onUpdated)
    }

    private fun lightweightHighlights(text: String): List<UiTextHighlight> {
        val starts = lineStarts(text)
        return text.split('\n').flatMapIndexed { lineIndex, line ->
            val textLine = runCatching { analyzer.lightweightHighlightLine(path, line) }.getOrElse {
                UnavailableKotlinScriptingAnalyzer.lightweightHighlightLine(path, line)
            }
            val lineStart = starts.getOrElse(lineIndex) { text.length }
            var cursor = lineStart
            textLine.spans.mapNotNull { (segment, style) ->
                val start = cursor
                val end = (start + segment.length).coerceAtMost(text.length)
                cursor = end
                if (start == end) null else UiTextHighlight(start, end, style.toUi())
            }
        }
    }
}

private data class AnalysisKey(
    val textHash: Int,
    val textLength: Int,
    val caret: Int,
)

private data class CompletionKey(
    val textHash: Int,
    val textLength: Int,
    val caret: Int,
)

private data class CompletionSnapshot(
    val textHash: Int,
    val textLength: Int,
    val caret: Int,
    val items: List<UiTextCompletion>,
) {
    fun matches(text: String, offset: Int): Boolean {
        return textHash == text.hashCode() && textLength == text.length && caret == offset.coerceIn(0, text.length)
    }

    companion object {
        val Empty = CompletionSnapshot(0, -1, 0, emptyList())
    }
}

private data class EditorAnalysisSnapshot(
    val text: String,
    val textHash: Int,
    val textLength: Int,
    val caret: Int,
    val highlights: List<UiTextHighlight>,
    val inlayHints: List<UiInlayHint>,
    val diagnostics: List<UiTextDiagnostic>,
) {
    fun matches(text: String, offset: Int): Boolean {
        return textHash == text.hashCode() && textLength == text.length && caret == offset.coerceIn(0, text.length)
    }

    fun matchesText(text: String): Boolean {
        return textHash == text.hashCode() && textLength == text.length
    }

    val hasText: Boolean get() = textLength >= 0

    fun highlightsForEditedText(
        editedText: String,
        fallback: (String) -> List<UiTextHighlight>,
    ): List<UiTextHighlight> {
        if (!hasText) return fallback(editedText)
        val commonPrefix = commonPrefixLength(text, editedText)
        val commonSuffix = commonSuffixLength(text, editedText, commonPrefix)
        val oldChangedStart = commonPrefix
        val oldChangedEnd = text.length - commonSuffix
        val newChangedStart = commonPrefix
        val newChangedEnd = editedText.length - commonSuffix
        val delta = editedText.length - text.length
        val newLineStart = editedText.lastIndexOf('\n', (newChangedStart - 1).coerceAtLeast(0))
            .let { if (it < 0) 0 else it + 1 }
        val newLineEnd = editedText.indexOf('\n', newChangedEnd.coerceIn(0, editedText.length))
            .let { if (it < 0) editedText.length else it }
        val unchanged = highlights.mapNotNull { highlight ->
            when {
                highlight.end <= oldChangedStart -> highlight
                highlight.start >= oldChangedEnd -> highlight.copy(
                    start = highlight.start + delta,
                    end = highlight.end + delta,
                )

                else -> null
            }
        }
        val changed = fallback(editedText).filter { highlight ->
            highlight.start < newLineEnd && highlight.end > newLineStart
        }
        return (unchanged + changed).sortedWith(compareBy<UiTextHighlight> { it.start }.thenBy { it.end })
    }

    fun inlayHintsForEditedText(editedText: String): List<UiInlayHint> {
        if (!hasText) return emptyList()
        return shiftInlayHintsForEditedText(text, editedText, inlayHints)
    }

    companion object {
        val Empty = EditorAnalysisSnapshot("", 0, -1, 0, emptyList(), emptyList(), emptyList())
    }
}

internal fun shiftInlayHintsForEditedText(
    originalText: String,
    editedText: String,
    inlayHints: List<UiInlayHint>,
): List<UiInlayHint> {
    if (inlayHints.isEmpty()) return emptyList()
    val commonPrefix = commonPrefixLength(originalText, editedText)
    val commonSuffix = commonSuffixLength(originalText, editedText, commonPrefix)
    val oldChangedStart = commonPrefix
    val oldChangedEnd = originalText.length - commonSuffix
    val delta = editedText.length - originalText.length
    return inlayHints.mapNotNull { hint ->
        when {
            hint.offset < oldChangedStart -> hint
            hint.offset >= oldChangedEnd -> hint.copy(offset = (hint.offset + delta).coerceIn(0, editedText.length))
            else -> null
        }
    }
}

private fun analyzerFor(path: String) = runCatching {
    EditorLanguageService(path.substringAfterLast('.', "")).analyzer
}.getOrElse {
    UnavailableKotlinScriptingAnalyzer
}

private fun List<TextLine>.toHighlights(text: String, lineStarts: List<Int>): List<UiTextHighlight> {
    return flatMapIndexed { lineIndex, line ->
        val lineStart = lineStarts.getOrElse(lineIndex) { text.length }
        var cursor = lineStart
        line.spans.mapNotNull { (segment, style) ->
            val start = cursor
            val end = (start + segment.length).coerceAtMost(text.length)
            cursor = end
            if (start == end) null else UiTextHighlight(start, end, style.toUi())
        }
    }
}

private fun List<TextLine>.toInlayHints(lineStarts: List<Int>, textLength: Int): List<UiInlayHint> {
    return flatMapIndexed { lineIndex, line ->
        val lineStart = lineStarts.getOrElse(lineIndex) { textLength }
        line.hints.map { hint ->
            UiInlayHint(
                offset = (lineStart + hint.index).coerceIn(0, textLength),
                text = hint.text,
            )
        }
    }
}

private fun CompletionItem.toUi(): UiTextCompletion {
    return UiTextCompletion(
        label = show,
        insertText = insert,
        detail = (this as? CompletionItem.Declaration)?.middle.orEmpty(),
        tail = (this as? CompletionItem.Declaration)?.tail.orEmpty(),
        icon = tag.completionIcon(),
        caretOffset = (insert.length + moveCaret).coerceIn(0, insert.length),
    )
}

private fun CompletionItemTag.completionIcon(): String {
    val name = when (this) {
        CompletionItemTag.FUNCTION -> "method"
        CompletionItemTag.PROPERTY,
        CompletionItemTag.LOCAL_VARIABLE -> "variable"
        CompletionItemTag.CLASS -> "class"
        CompletionItemTag.KEYWORD -> "package"
    }
    return "hollowengine:textures/gui/icons/autocomplete_$name.svg"
}

private fun Diagnostic.toUi(text: String, lineStarts: List<Int>): UiTextDiagnostic {
    val start = offsetAt(lineStarts, range.start.line, range.start.column, text.length)
    val end = offsetAt(lineStarts, range.end.line, range.end.column, text.length).coerceAtLeast(start)
    return UiTextDiagnostic(
        start = start,
        end = end,
        message = message,
        severity = severity.toUi(),
        line = range.start.line + 1,
        column = range.start.column + 1,
    )
}

private fun Severity.toUi(): UiTextDiagnosticSeverity {
    return when (this) {
        Severity.ERROR,
        Severity.FATAL -> UiTextDiagnosticSeverity.ERROR

        Severity.WARNING -> UiTextDiagnosticSeverity.WARNING
        Severity.DEBUG,
        Severity.INFO -> UiTextDiagnosticSeverity.INFO
    }
}

private fun SpanStyle.toUi(): UiInlineStyle {
    var style = UiInlineStyle().withColor(color.toUiColor())
    if (highlight) style = style.withBackground(color.toUiColor().copy(alpha = 0.24f))
    if (bold) style = style.withBold()
    if (italic) style = style.withItalic()
    return style
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

private fun TokenType.toUiColor(): UiColor {
    return when (this) {
        TokenType.COMMENT -> UiColor(0.55f, 0.6f, 0.68f, 1f)
        TokenType.KEYWORD -> UiColor(0.81f, 0.56f, 0.43f, 1f)
        TokenType.STRING -> UiColor(0.42f, 0.67f, 0.45f, 1f)
        TokenType.ANNOTATION -> UiColor(0.7f, 0.68f, 0.38f, 1f)
        TokenType.NUMERIC_LITERAL -> UiColor(0.16f, 0.68f, 0.72f, 1f)
        TokenType.PROPERTY_IDENTIFIER,
        TokenType.FIELD -> UiColor(0.78f, 0.49f, 0.73f, 1f)

        TokenType.VARIABLE,
        TokenType.PARAMETER,
        TokenType.NAME_REFERENCE,
        TokenType.CLASS,
        TokenType.INTERFACE,
        TokenType.ENUM,
        TokenType.OBJECT -> UiColor(0.66f, 0.72f, 0.78f, 1f)

        TokenType.EXTENSION_RECEIVER,
        TokenType.VALUE_ARGUMENT_NAME -> UiColor(0.34f, 0.66f, 0.97f, 1f)

        TokenType.TOP_LEVEL -> UiColor(0.95f, 0.96f, 0.95f, 1f)
        TokenType.FUNCTION,
        TokenType.METHOD -> UiColor(1f, 0.78f, 0.43f, 1f)

        TokenType.DEFAULT -> UiColor(0.84f, 0.87f, 0.92f, 1f)
    }
}

private fun lineStarts(text: String): List<Int> {
    val starts = mutableListOf(0)
    text.forEachIndexed { index, char ->
        if (char == '\n') starts += index + 1
    }
    return starts
}

private fun offsetAt(lineStarts: List<Int>, line: Int, column: Int, textLength: Int): Int {
    val lineStart = lineStarts.getOrElse(line.coerceAtLeast(0)) { textLength }
    return (lineStart + column.coerceAtLeast(0)).coerceIn(0, textLength)
}
