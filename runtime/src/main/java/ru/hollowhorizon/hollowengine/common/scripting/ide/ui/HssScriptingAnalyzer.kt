package ru.hollowhorizon.hollowengine.common.scripting.ide.ui

import ru.hollowhorizon.hollowengine.common.scripting.ide.CompletionItem
import ru.hollowhorizon.hollowengine.common.scripting.ide.DefinitionLocation
import ru.hollowhorizon.hollowengine.common.scripting.ide.Diagnostic
import ru.hollowhorizon.hollowengine.common.scripting.ide.InlayHint
import ru.hollowhorizon.hollowengine.common.scripting.ide.ResourceLocationTargets
import ru.hollowhorizon.hollowengine.common.scripting.ide.ScriptingAnalyzer
import ru.hollowhorizon.hollowengine.common.scripting.ide.SpanStyle
import ru.hollowhorizon.hollowengine.common.scripting.ide.TextLine
import ru.hollowhorizon.hollowengine.common.scripting.ide.TokenType

/**
 * IDE support for HSS. Highlighting, completion, hints and diagnostics all read the same
 * two sources of truth: the scanner in [HssLexer] and the property schema behind
 * [UiLanguageCatalog], so the editor never disagrees with the compiler.
 */
object HssScriptingAnalyzer : ScriptingAnalyzer {
    override fun highlight(name: String, text: String, offset: Int): List<TextLine> {
        val model = HssDocumentModel(text)
        val spans = HssLexer(text, model.keyframeNames).tokenize()
        return buildTextLines(text, spans, hssInlayHints(model))
    }

    override fun lightweightHighlightLine(name: String, line: String): TextLine {
        val spans = HssLexer(line, initialDepth = lineDepthGuess(line)).tokenize()
        return buildTextLines(line, spans, emptyList()).firstOrNull() ?: TextLine(emptyList(), ArrayList())
    }

    override fun completions(name: String, text: String, offset: Int): List<CompletionItem> =
        hssCompletions(text, offset)

    override fun diagnostic(name: String, text: String): List<Diagnostic> = hssDiagnostics(text)

    /**
     * Jumps from a resource location to the file it names, and from an animation name to
     * the `@keyframes` block that defines it.
     */
    override fun definition(name: String, text: String, offset: Int): DefinitionLocation? {
        val caret = offset.coerceIn(0, text.length)
        hssLocationAt(text, caret)?.let { location ->
            ResourceLocationTargets.definition(location)?.let { return it }
        }
        val word = wordAt(text, caret) ?: return null
        val keyframes = HssDocumentModel(text).keyframesNamed(word) ?: return null
        return DefinitionLocation(name, keyframes)
    }

    private fun wordAt(text: String, caret: Int): String? {
        var start = caret
        while (start > 0 && text[start - 1].isKeyframeNameChar()) start--
        var end = caret
        while (end < text.length && text[end].isKeyframeNameChar()) end++
        return text.substring(start, end).takeIf { it.isNotEmpty() }
    }

    private fun Char.isKeyframeNameChar(): Boolean = isLetterOrDigit() || this == '-' || this == '_'

    /**
     * A standalone line has no block context, so a `property: value` shaped line is scanned
     * as a declaration and everything else as a selector.
     */
    private fun lineDepthGuess(line: String): Int {
        val trimmed = line.trim()
        val declarationShaped = ':' in trimmed && '{' !in trimmed && !trimmed.startsWith('@')
        return if (declarationShaped) 1 else 0
    }

    private fun buildTextLines(text: String, spans: List<HssSpan>, hints: List<InlayHint>): List<TextLine> {
        val hintsByOffset = hints.groupBy { it.index }
        val lines = text.split('\n')
        val result = ArrayList<TextLine>(lines.size)
        var lineStart = 0
        var spanIndex = 0
        for (line in lines) {
            val lineEnd = lineStart + line.length
            val pieces = ArrayList<Pair<String, SpanStyle>>()
            var cursor = lineStart
            while (spanIndex < spans.size && spans[spanIndex].start < lineEnd) {
                val span = spans[spanIndex]
                val start = maxOf(span.start, cursor)
                val end = minOf(span.end, lineEnd)
                if (end > start) {
                    if (start > cursor) pieces += text.substring(cursor, start) to DefaultStyle
                    pieces += text.substring(start, end) to span.style()
                    cursor = end
                }
                if (span.end <= lineEnd) spanIndex++ else break
            }
            if (cursor < lineEnd) pieces += text.substring(cursor, lineEnd) to DefaultStyle
            result += TextLine(pieces, lineHints(hintsByOffset, lineStart, lineEnd))
            lineStart = lineEnd + 1
        }
        return result
    }

    private fun lineHints(
        hintsByOffset: Map<Int, List<InlayHint>>,
        lineStart: Int,
        lineEnd: Int,
    ): ArrayList<InlayHint> {
        val hints = ArrayList<InlayHint>()
        if (hintsByOffset.isEmpty()) return hints
        for (offset in lineStart..lineEnd) {
            hintsByOffset[offset]?.forEach { hints += InlayHint(offset - lineStart, it.text) }
        }
        return hints
    }

    private fun HssSpan.style(): SpanStyle = SpanStyle(type, italic = italic, bold = false, highlight = false)

    private val DefaultStyle = SpanStyle(TokenType.DEFAULT, italic = false, bold = false, highlight = false)
}
