package ru.hollowhorizon.hollowengine.common.scripting.ide

object UnavailableKotlinScriptingAnalyzer : ScriptingAnalyzer {
    private val defaultStyle = SpanStyle(TokenType.DEFAULT, italic = false, bold = false, highlight = false)

    override fun highlight(name: String, text: String, offset: Int): List<TextLine> {
        return text.lines().map { line ->
            TextLine(listOf(line to defaultStyle), ArrayList())
        }
    }

    override fun lightweightHighlightLine(name: String, line: String): TextLine {
        return TextLine(listOf(line to defaultStyle), ArrayList())
    }

    override fun completions(name: String, text: String, offset: Int): List<CompletionItem> {
        return emptyList()
    }

    override fun diagnostic(name: String, text: String): List<Diagnostic> {
        return listOf(
            Diagnostic(
                Range(Position(0, 0), Position.at(text, text.length)),
                Severity.WARNING,
                "Kotlin scripting compiler addon is not installed"
            )
        )
    }
}
