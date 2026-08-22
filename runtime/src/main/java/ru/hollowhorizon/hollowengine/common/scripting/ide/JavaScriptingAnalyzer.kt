package ru.hollowhorizon.hollowengine.common.scripting.ide

/**
 * Lightweight lexer-based highlighter for Java sources (read-only decompiled/attached files opened
 * via go-to-definition). No compiler behind it: keywords, literals, comments, annotations and
 * type-looking identifiers only; no completions and no diagnostics.
 */
object JavaScriptingAnalyzer : ScriptingAnalyzer {
    private val KEYWORDS = setOf(
        "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const",
        "continue", "default", "do", "double", "else", "enum", "extends", "final", "finally", "float",
        "for", "goto", "if", "implements", "import", "instanceof", "int", "interface", "long", "native",
        "new", "package", "private", "protected", "public", "record", "return", "sealed", "short",
        "static", "strictfp", "super", "switch", "synchronized", "this", "throw", "throws", "transient",
        "try", "var", "void", "volatile", "while", "yield", "true", "false", "null",
    )

    private val defaultStyle = SpanStyle(TokenType.DEFAULT, italic = false, bold = false, highlight = false)
    private val keywordStyle = SpanStyle(TokenType.KEYWORD, italic = false, bold = false, highlight = false)
    private val stringStyle = SpanStyle(TokenType.STRING, italic = false, bold = false, highlight = false)
    private val numberStyle = SpanStyle(TokenType.NUMERIC_LITERAL, italic = false, bold = false, highlight = false)
    private val commentStyle = SpanStyle(TokenType.COMMENT, italic = true, bold = false, highlight = false)
    private val annotationStyle = SpanStyle(TokenType.ANNOTATION, italic = false, bold = false, highlight = false)
    private val typeStyle = SpanStyle(TokenType.CLASS, italic = false, bold = false, highlight = false)
    private val methodStyle = SpanStyle(TokenType.METHOD, italic = false, bold = false, highlight = false)

    override fun highlight(name: String, text: String, offset: Int): List<TextLine> {
        var inBlockComment = false
        return text.lines().map { line ->
            val (spans, stillInComment) = tokenizeLine(line, inBlockComment)
            inBlockComment = stillInComment
            TextLine(spans, ArrayList())
        }
    }

    override fun lightweightHighlightLine(name: String, line: String): TextLine {
        return TextLine(tokenizeLine(line, startsInBlockComment = false).first, ArrayList())
    }

    override fun diagnostic(name: String, text: String): List<Diagnostic> = emptyList()

    private fun tokenizeLine(
        line: String,
        startsInBlockComment: Boolean,
    ): Pair<List<Pair<String, SpanStyle>>, Boolean> {
        val spans = mutableListOf<Pair<String, SpanStyle>>()
        var inBlockComment = startsInBlockComment
        var i = 0

        fun emit(start: Int, end: Int, style: SpanStyle) {
            if (end > start) spans.add(line.substring(start, end) to style)
        }

        while (i < line.length) {
            val start = i
            val char = line[i]
            when {
                inBlockComment -> {
                    val close = line.indexOf("*/", i)
                    if (close < 0) {
                        emit(i, line.length, commentStyle)
                        i = line.length
                    } else {
                        emit(i, close + 2, commentStyle)
                        i = close + 2
                        inBlockComment = false
                    }
                }

                char == '/' && i + 1 < line.length && line[i + 1] == '/' -> {
                    emit(i, line.length, commentStyle)
                    i = line.length
                }

                char == '/' && i + 1 < line.length && line[i + 1] == '*' -> {
                    val close = line.indexOf("*/", i + 2)
                    if (close < 0) {
                        emit(i, line.length, commentStyle)
                        i = line.length
                        inBlockComment = true
                    } else {
                        emit(i, close + 2, commentStyle)
                        i = close + 2
                    }
                }

                char == '"' || char == '\'' -> {
                    i++
                    while (i < line.length && line[i] != char) {
                        if (line[i] == '\\') i++
                        i++
                    }
                    if (i < line.length) i++
                    emit(start, i, stringStyle)
                }

                char == '@' && i + 1 < line.length && line[i + 1].isJavaIdentifierStart() -> {
                    i++
                    while (i < line.length && line[i].isJavaIdentifierPart()) i++
                    emit(start, i, annotationStyle)
                }

                char.isDigit() -> {
                    while (i < line.length && (line[i].isLetterOrDigit() || line[i] == '.' || line[i] == '_')) i++
                    emit(start, i, numberStyle)
                }

                char.isJavaIdentifierStart() -> {
                    while (i < line.length && line[i].isJavaIdentifierPart()) i++
                    val word = line.substring(start, i)
                    val style = when {
                        word in KEYWORDS -> keywordStyle
                        word.first().isUpperCase() -> typeStyle
                        line.getOrNull(line.indexOfFirstNonSpace(i)) == '(' -> methodStyle
                        else -> defaultStyle
                    }
                    emit(start, i, style)
                }

                else -> {
                    i++
                    while (i < line.length && !line[i].isJavaTokenBoundary()) i++
                    emit(start, i, defaultStyle)
                }
            }
        }
        if (spans.isEmpty()) spans.add(line to defaultStyle)
        return spans to inBlockComment
    }

    private fun String.indexOfFirstNonSpace(from: Int): Int {
        var index = from
        while (index < length && this[index] == ' ') index++
        return index
    }

    private fun Char.isJavaTokenBoundary(): Boolean =
        isJavaIdentifierStart() || isDigit() || this == '"' || this == '\'' || this == '/' || this == '@'
}

/** Fallback for file types without a language service: plain text, no diagnostics, no stub warnings. */
object PlainTextScriptingAnalyzer : ScriptingAnalyzer {
    override fun highlight(name: String, text: String, offset: Int): List<TextLine> = emptyList()

    override fun diagnostic(name: String, text: String): List<Diagnostic> = emptyList()
}
