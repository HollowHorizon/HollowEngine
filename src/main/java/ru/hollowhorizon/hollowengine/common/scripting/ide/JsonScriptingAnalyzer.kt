package ru.hollowhorizon.hollowengine.common.scripting.ide


object JsonScriptingAnalyzer : ScriptingAnalyzer {
    private val JSON_KEYWORDS = setOf("true", "false", "null")

    override fun highlight(name: String, text: String, offset: Int): List<TextLine> {
        val lines = text.lines()
        return lines.map { line ->
            val spans = tokenizeLine(line)
            TextLine(spans, ArrayList())
        }
    }

    private fun tokenizeLine(line: String): List<Pair<String, SpanStyle>> {
        val spans = mutableListOf<Pair<String, SpanStyle>>()
        var i = 0

        while (i < line.length) {
            val char = line[i]

            when {
                char.isWhitespace() -> {
                    val start = i
                    while (i < line.length && line[i].isWhitespace()) i++
                    spans.add(line.substring(start, i) to SpanStyle(TokenType.DEFAULT, false, false, false))
                }

                // String literal
                char == '"' -> {
                    val start = i
                    i++ // skip opening quote
                    while (i < line.length) {
                        if (line[i] == '\\' && i + 1 < line.length) {
                            i += 2 // skip escaped character
                        } else if (line[i] == '"') {
                            i++ // skip closing quote
                            break
                        } else {
                            i++
                        }
                    }
                    spans.add(line.substring(start, i) to SpanStyle(TokenType.STRING, false, false, false))
                }

                char == '-' || char.isDigit() -> {
                    val start = i
                    if (char == '-') i++
                    while (i < line.length && (line[i].isDigit() || line[i] == '.' || line[i] == 'e' || line[i] == 'E' || line[i] == '+' || line[i] == '-')) {
                        if ((line[i] == 'e' || line[i] == 'E') && i + 1 < line.length) {
                            i++
                            if (line[i] == '+' || line[i] == '-') i++
                        } else {
                            i++
                        }
                    }
                    spans.add(line.substring(start, i) to SpanStyle(TokenType.NUMERIC_LITERAL, false, false, false))
                }

                char.isLetter() -> {
                    val start = i
                    while (i < line.length && line[i].isLetter()) i++
                    val word = line.substring(start, i)
                    val tokenType = if (word in JSON_KEYWORDS) TokenType.KEYWORD else TokenType.DEFAULT
                    spans.add(word to SpanStyle(tokenType, false, false, false))
                }

                char == '{' || char == '}' || char == '[' || char == ']' || char == ':' || char == ',' -> {
                    spans.add(char.toString() to SpanStyle(TokenType.DEFAULT, false, false, false))
                    i++
                }

                else -> {
                    spans.add(char.toString() to SpanStyle(TokenType.DEFAULT, false, false, false))
                    i++
                }
            }
        }

        return spans
    }

    override fun completions(name: String, text: String, offset: Int): List<CompletionItem> {
        return emptyList()
    }

    override fun diagnostic(name: String, text: String): List<Diagnostic> {
        val diagnostics = mutableListOf<Diagnostic>()

        var braceCount = 0
        var bracketCount = 0
        var inString = false
        var escapeNext = false
        var lineIndex = 0
        var columnIndex = 0

        for ((lineNum, line) in text.lines().withIndex()) {
            columnIndex = 0
            for ((colNum, char) in line.withIndex()) {
                columnIndex = colNum

                if (escapeNext) {
                    escapeNext = false
                    continue
                }

                when {
                    char == '\\' && inString -> escapeNext = true
                    char == '"' -> inString = !inString
                    !inString -> {
                        when (char) {
                            '{' -> braceCount++
                            '}' -> {
                                braceCount--
                                if (braceCount < 0) {
                                    diagnostics.add(
                                        Diagnostic(
                                            Range(Position(lineNum, colNum), Position(lineNum, colNum + 1)),
                                            Severity.ERROR,
                                            "Unexpected closing brace '}'"
                                        )
                                    )
                                }
                            }
                            '[' -> bracketCount++
                            ']' -> {
                                bracketCount--
                                if (bracketCount < 0) {
                                    diagnostics.add(
                                        Diagnostic(
                                            Range(Position(lineNum, colNum), Position(lineNum, colNum + 1)),
                                            Severity.ERROR,
                                            "Unexpected closing bracket ']'"
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
            lineIndex = lineNum
        }

        // Check for unclosed braces/brackets
        if (braceCount > 0) {
            diagnostics.add(
                Diagnostic(
                    Range(Position(lineIndex, columnIndex), Position(lineIndex, columnIndex + 1)),
                    Severity.ERROR,
                    "Missing $braceCount closing brace(s) '}'"
                )
            )
        } else if (braceCount < 0) {
            diagnostics.add(
                Diagnostic(
                    Range(Position(0, 0), Position(0, 1)),
                    Severity.ERROR,
                    "Extra ${-braceCount} closing brace(s) '}'"
                )
            )
        }

        if (bracketCount > 0) {
            diagnostics.add(
                Diagnostic(
                    Range(Position(lineIndex, columnIndex), Position(lineIndex, columnIndex + 1)),
                    Severity.ERROR,
                    "Missing $bracketCount closing bracket(s) ']'"
                )
            )
        } else if (bracketCount < 0) {
            diagnostics.add(
                Diagnostic(
                    Range(Position(0, 0), Position(0, 1)),
                    Severity.ERROR,
                    "Extra ${-bracketCount} closing bracket(s) ']'"
                )
            )
        }

        // Check for unclosed strings
        if (inString) {
            diagnostics.add(
                Diagnostic(
                    Range(Position(0, 0), Position(0, 1)),
                    Severity.ERROR,
                    "Unclosed string literal"
                )
            )
        }

        return diagnostics
    }
}