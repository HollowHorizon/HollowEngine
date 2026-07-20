package ru.hollowhorizon.hollowengine.common.scripting.ide

interface ScriptingAnalyzer {
    fun highlight(name: String, text: String, offset: Int): List<TextLine>

    /** Ranges to highlight for the symbol/bracket at [offset]; cheap compared to [highlight]. */
    fun occurrences(name: String, text: String, offset: Int): List<OccurrenceRange> = emptyList()

    fun lightweightHighlightLine(name: String, line: String): TextLine {
        return TextLine(listOf(line to SpanStyle(TokenType.DEFAULT, italic = false, bold = false, highlight = false)), ArrayList())
    }

    fun completions(name: String, text: String, offset: Int): List<CompletionItem>
    fun definition(name: String, text: String, offset: Int): DefinitionLocation? = null
    fun diagnostic(name: String, text: String): List<Diagnostic>
}

data class OccurrenceRange(
    val start: Int,
    val end: Int,
)

data class DefinitionLocation(
    val path: String,
    val offset: Int = 0,
    val text: String? = null,
    val readOnly: Boolean = text != null,
)

data class InlayHint(
    val index: Int,
    val text: String,
)

data class TextLine(val spans: List<Pair<String, SpanStyle>>, val hints: ArrayList<InlayHint>)

data class SpanStyle(
    val color: TokenType,
    val italic: Boolean,
    val bold: Boolean,
    val highlight: Boolean,
)
