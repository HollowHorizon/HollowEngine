package ru.hollowhorizon.hollowengine.common.scripting.ide


object JsonScriptingAnalyzer : ScriptingAnalyzer {
    private val JSON_KEYWORDS = setOf("true", "false", "null")
    private val MAX_SEARCH_LINES = 70

    override fun highlight(name: String, text: String, offset: Int): List<TextLine> {
        val lines = text.lines()

        var currentOffset = 0
        var caretLineIndex = -1
        var caretPosInLine = -1

        for ((index, line) in lines.withIndex()) {
            if (currentOffset + line.length >= offset) {
                caretLineIndex = index
                caretPosInLine = offset - currentOffset
                break
            }
            currentOffset += line.length + 1
        }

        val linesToUpdate = mutableSetOf<Int>()
        var matchingBracketPos: Pair<Int, Int>? = null
        var bracketAtCaret: Char? = null

        if (caretLineIndex >= 0 && caretLineIndex < lines.size) {
            val line = lines[caretLineIndex]
            linesToUpdate.add(caretLineIndex)

            if (caretPosInLine in line.indices) {
                val char = line[caretPosInLine]
                if (char in "{}[]") {
                    bracketAtCaret = char
                    matchingBracketPos =
                        findMatchingBracketWithLimit(lines, caretLineIndex, caretPosInLine)
                }
            }

            if (bracketAtCaret == null && caretPosInLine - 1 in line.indices) {
                val char = line[caretPosInLine - 1]
                if (char in "{}[]") {
                    matchingBracketPos =
                        findMatchingBracketWithLimit(lines, caretLineIndex, caretPosInLine - 1)
                }
            }

            if (matchingBracketPos != null) {
                linesToUpdate.add(matchingBracketPos.first)
            }
        }

        return lines.mapIndexed { lineIndex, line ->
            val spans = if (lineIndex in linesToUpdate) {
                tokenizeLine(line, lineIndex, caretLineIndex, caretPosInLine, matchingBracketPos)
            } else {
                tokenizeLineSimple(line)
            }
            TextLine(spans, ArrayList())
        }
    }

    override fun lightweightHighlightLine(name: String, line: String): TextLine =
        TextLine(tokenizeLineSimple(line), ArrayList())

    override fun completions(name: String, text: String, offset: Int, sink: CompletionSink) {
        completeRecipeItems(name, text, offset, sink)
    }

    private fun tokenizeLineSimple(line: String): List<Pair<String, SpanStyle>> {
        val spans = mutableListOf<Pair<String, SpanStyle>>()
        var i = 0
        val defaultStyle = SpanStyle(TokenType.DEFAULT, false, false, false)
        val stringStyle = SpanStyle(TokenType.STRING, false, false, false)
        val numericStyle = SpanStyle(TokenType.NUMERIC_LITERAL, false, false, false)

        while (i < line.length) {
            val char = line[i]

            when {
                char.isWhitespace() -> {
                    val start = i
                    while (i < line.length && line[i].isWhitespace()) i++
                    spans.add(line.substring(start, i) to defaultStyle)
                }

                char == '"' -> {
                    val start = i
                    i++
                    while (i < line.length) {
                        if (line[i] == '\\' && i + 1 < line.length) {
                            i += 2
                        } else if (line[i] == '"') {
                            i++
                            break
                        } else {
                            i++
                        }
                    }
                    spans.add(line.substring(start, i) to stringStyle)
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
                    spans.add(line.substring(start, i) to numericStyle)
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

    private fun tokenizeLine(
        line: String,
        currentLineIndex: Int,
        caretLineIndex: Int,
        caretPosInLine: Int,
        matchingBracketPos: Pair<Int, Int>?,
    ): List<Pair<String, SpanStyle>> {
        val spans = mutableListOf<Pair<String, SpanStyle>>()
        var i = 0
        val defaultStyle = SpanStyle(TokenType.DEFAULT, false, false, false)
        val stringStyle = SpanStyle(TokenType.STRING, false, false, false)
        val numericStyle = SpanStyle(TokenType.NUMERIC_LITERAL, false, false, false)

        while (i < line.length) {
            val char = line[i]
            val style = getSpanStyle(
                currentLineIndex, i, char,
                caretLineIndex, caretPosInLine,
                matchingBracketPos
            )

            when {
                char.isWhitespace() -> {
                    val start = i
                    while (i < line.length && line[i].isWhitespace()) i++
                    spans.add(line.substring(start, i) to defaultStyle)
                }

                char == '"' -> {
                    val start = i
                    i++
                    while (i < line.length) {
                        if (line[i] == '\\' && i + 1 < line.length) {
                            i += 2
                        } else if (line[i] == '"') {
                            i++
                            break
                        } else {
                            i++
                        }
                    }
                    spans.add(line.substring(start, i) to stringStyle)
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
                    spans.add(line.substring(start, i) to numericStyle)
                }

                char.isLetter() -> {
                    val start = i
                    while (i < line.length && line[i].isLetter()) i++
                    val word = line.substring(start, i)
                    val tokenType = if (word in JSON_KEYWORDS) TokenType.KEYWORD else TokenType.DEFAULT
                    spans.add(word to SpanStyle(tokenType, false, false, false))
                }

                char == '{' || char == '}' || char == '[' || char == ']' || char == ':' || char == ',' -> {
                    spans.add(char.toString() to style)
                    i++
                }

                else -> {
                    spans.add(char.toString() to style)
                    i++
                }
            }
        }

        return spans
    }

    private fun getSpanStyle(
        currentLineIndex: Int,
        position: Int,
        char: Char,
        caretLineIndex: Int,
        caretPosInLine: Int,
        matchingBracketPos: Pair<Int, Int>?,
    ): SpanStyle {
        val defaultStyle = SpanStyle(TokenType.DEFAULT, false, false, false)

        if (currentLineIndex == caretLineIndex && position == caretPosInLine - 1 && (char == '{' || char == '}' || char == '[' || char == ']')) {
            return SpanStyle(TokenType.DEFAULT, false, false, true)
        }

        if (currentLineIndex == caretLineIndex && position == caretPosInLine && (char == '{' || char == '}' || char == '[' || char == ']')) {
            return SpanStyle(TokenType.DEFAULT, false, false, true)
        }

        if (matchingBracketPos != null && currentLineIndex == matchingBracketPos.first && position == matchingBracketPos.second) {
            return SpanStyle(TokenType.DEFAULT, false, false, true)
        }

        return defaultStyle
    }

    private fun findMatchingBracketWithLimit(
        lines: List<String>,
        lineIndex: Int,
        posInLine: Int,
    ): Pair<Int, Int>? {
        if (posInLine < 0 || posInLine >= lines[lineIndex].length) return null

        val char = lines[lineIndex][posInLine]

        if (char !in "{}[]") return null

        val openBrackets = mapOf('{' to '}', '[' to ']')
        val closeBrackets = mapOf('}' to '{', ']' to '[')

        return when {
            char in openBrackets.keys -> findClosingBracketWithLimit(
                lines,
                lineIndex,
                posInLine,
                char,
                openBrackets[char]!!
            )

            char in closeBrackets.keys -> findOpeningBracketWithLimit(
                lines,
                lineIndex,
                posInLine,
                char,
                closeBrackets[char]!!
            )

            else -> null
        }
    }

    private fun findClosingBracketWithLimit(
        lines: List<String>,
        startLine: Int,
        startPos: Int,
        opening: Char,
        closing: Char,
    ): Pair<Int, Int>? {
        var count = 1
        var line = startLine
        var pos = startPos + 1
        val maxLine = minOf(startLine + MAX_SEARCH_LINES, lines.size)

        while (line < maxLine) {
            val currentLine = lines[line]
            while (pos < currentLine.length) {
                val c = currentLine[pos]
                if (c == opening) count++
                else if (c == closing) {
                    count--
                    if (count == 0) return Pair(line, pos)
                }
                pos++
            }
            line++
            pos = 0
        }
        return null
    }

    private fun findOpeningBracketWithLimit(
        lines: List<String>,
        startLine: Int,
        startPos: Int,
        closing: Char,
        opening: Char,
    ): Pair<Int, Int>? {
        var count = 1
        var line = startLine
        var pos = startPos - 1
        val minLine = maxOf(startLine - MAX_SEARCH_LINES, 0)

        while (line >= minLine) {
            val currentLine = lines[line]
            while (pos >= 0) {
                val c = currentLine[pos]
                if (c == closing) count++
                else if (c == opening) {
                    count--
                    if (count == 0) return Pair(line, pos)
                }
                pos--
            }
            line--
            if (line >= minLine) pos = lines[line].length - 1
        }
        return null
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
