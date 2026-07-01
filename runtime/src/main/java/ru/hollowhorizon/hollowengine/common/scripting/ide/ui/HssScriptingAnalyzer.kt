package ru.hollowhorizon.hollowengine.common.scripting.ide.ui

import ru.hollowhorizon.hollowengine.client.ui.style.HssParseException
import ru.hollowhorizon.hollowengine.client.ui.style.parseHss
import ru.hollowhorizon.hollowengine.common.scripting.ide.*

object HssScriptingAnalyzer : ScriptingAnalyzer {
    private val defaultStyle = SpanStyle(TokenType.DEFAULT, italic = false, bold = false, highlight = false)
    private val selectorStyle = SpanStyle(TokenType.CLASS, italic = false, bold = false, highlight = false)
    private val propertyStyle =
        SpanStyle(TokenType.PROPERTY_IDENTIFIER, italic = false, bold = false, highlight = false)
    private val stringStyle = SpanStyle(TokenType.STRING, italic = false, bold = false, highlight = false)
    private val numberStyle = SpanStyle(TokenType.NUMERIC_LITERAL, italic = false, bold = false, highlight = false)
    private val commentStyle = SpanStyle(TokenType.COMMENT, italic = true, bold = false, highlight = false)

    override fun highlight(name: String, text: String, offset: Int): List<TextLine> {
        var inStyleBlock = false
        var inBlockComment = false
        return text.lines().map { line ->
            val result = tokenizeLine(line, inStyleBlock, inBlockComment)
            inStyleBlock = result.inStyleBlock
            inBlockComment = result.inBlockComment
            TextLine(result.spans, ArrayList())
        }
    }

    override fun lightweightHighlightLine(name: String, line: String): TextLine {
        return TextLine(tokenizeLine(line, false, false).spans, ArrayList())
    }

    override fun completions(name: String, text: String, offset: Int): List<CompletionItem> {
        val context = HssCompletionContext.from(text, offset) ?: return emptyList()
        val values = when (context.kind) {
            HssCompletionKind.SELECTOR_TYPE -> UiLanguageCatalog.elementTypes
            HssCompletionKind.STATE -> UiLanguageCatalog.states
            HssCompletionKind.PROPERTY -> UiLanguageCatalog.hssProperties
            HssCompletionKind.VALUE -> UiLanguageCatalog.valuesFor(context.property)
        }
        return values.asSequence()
            .filter { it.startsWith(context.prefix, ignoreCase = true) }
            .map { completion(it, if (context.kind == HssCompletionKind.PROPERTY) "$it: " else it, context.kind) }
            .toList()
    }

    override fun diagnostic(name: String, text: String): List<Diagnostic> {
        return try {
            parseHss(text)
            emptyList()
        } catch (exception: HssParseException) {
            listOf(diagnosticAt(text, exception.position, exception.messageText))
        } catch (exception: IllegalArgumentException) {
            listOf(diagnosticAt(text, 0, exception.message ?: "Invalid HSS"))
        }
    }

    private fun tokenizeLine(line: String, initialStyleBlock: Boolean, initialBlockComment: Boolean): HssLineTokens {
        val spans = mutableListOf<Pair<String, SpanStyle>>()
        var index = 0
        var inStyleBlock = initialStyleBlock
        var inBlockComment = initialBlockComment

        while (index < line.length) {
            when {
                inBlockComment -> {
                    val close = line.indexOf("*/", index)
                    val end = if (close < 0) line.length else close + 2
                    spans += line.substring(index, end) to commentStyle
                    index = end
                    inBlockComment = close < 0
                }

                line.startsWith("/*", index) -> {
                    val close = line.indexOf("*/", index + 2)
                    val end = if (close < 0) line.length else close + 2
                    spans += line.substring(index, end) to commentStyle
                    index = end
                    inBlockComment = close < 0
                }

                line.startsWith("//", index) -> {
                    spans += line.substring(index) to commentStyle
                    index = line.length
                }

                line[index] == '{' -> {
                    spans += "{" to defaultStyle
                    index++
                    inStyleBlock = true
                }

                line[index] == '}' -> {
                    spans += "}" to defaultStyle
                    index++
                    inStyleBlock = false
                }

                inStyleBlock -> {
                    index = tokenizeInsideBlock(line, index, spans)
                }

                else -> {
                    index = tokenizeOutsideBlock(line, index, spans)
                }
            }
        }
        return HssLineTokens(spans, inStyleBlock, inBlockComment)
    }

    private fun tokenizeInsideBlock(line: String, startIndex: Int, spans: MutableList<Pair<String, SpanStyle>>): Int {
        var index = startIndex
        val char = line[index]

        when {
            char == '"' || char == '\'' -> {
                index = tokenizeString(line, index, spans)
            }

            char.isDigit() || char == '#' -> {
                index = tokenizeNumber(line, index, spans)
            }

            isIdentifierStart(char) -> {
                val start = index++
                while (index < line.length && isIdentifierPart(line[index])) index++

                val style = if (nextNonWhitespace(line, index) == ':') propertyStyle else selectorStyle
                spans += line.substring(start, index) to style
            }

            else -> {
                spans += char.toString() to defaultStyle
                index++
            }
        }
        return index
    }

    private fun tokenizeOutsideBlock(line: String, startIndex: Int, spans: MutableList<Pair<String, SpanStyle>>): Int {
        var index = startIndex
        val char = line[index]

        when {
            isIdentifierStart(char) -> {
                val start = index++
                while (index < line.length && isIdentifierPart(line[index])) index++
                spans += line.substring(start, index) to selectorStyle
            }

            char == '"' || char == '\'' -> {
                index = tokenizeString(line, index, spans)
            }

            else -> {
                spans += char.toString() to defaultStyle
                index++
            }
        }
        return index
    }

    private fun tokenizeString(line: String, startIndex: Int, spans: MutableList<Pair<String, SpanStyle>>): Int {
        var index = startIndex
        val quote = line[index++]
        while (index < line.length && line[index] != quote) index++
        if (index < line.length) index++
        spans += line.substring(startIndex, index) to stringStyle
        return index
    }

    private fun tokenizeNumber(line: String, startIndex: Int, spans: MutableList<Pair<String, SpanStyle>>): Int {
        var index = startIndex + 1
        while (index < line.length && !line[index].isWhitespace() && line[index] !in ";,(){}") index++
        spans += line.substring(startIndex, index) to numberStyle
        return index
    }

    private fun completion(show: String, insert: String, kind: HssCompletionKind): CompletionItem {
        return declarationCompletionItem {
            this.show = show
            this.insert = insert
            name = show
            tag = CompletionItemTag.PROPERTY
            fqName = null
            tail = if (kind == HssCompletionKind.VALUE) "value" else null
            middle = null
        }
    }

    private fun diagnosticAt(text: String, offset: Int, message: String): Diagnostic {
        val position = positionAt(text, offset)
        return Diagnostic(Range(position, position.copy(column = position.column + 1)), Severity.ERROR, message)
    }

    private fun positionAt(text: String, offset: Int): Position {
        var line = 0
        var column = 0
        for (index in 0 until offset.coerceIn(0, text.length)) {
            if (text[index] == '\n') {
                line++
                column = 0
            } else {
                column++
            }
        }
        return Position(line, column)
    }

    private fun isIdentifierStart(char: Char): Boolean = char.isLetter() || char == '-' || char == '_' || char == '.'

    private fun isIdentifierPart(char: Char): Boolean = isIdentifierStart(char) || char.isDigit()

    private fun nextNonWhitespace(line: String, start: Int): Char? {
        var index = start
        while (index < line.length && line[index].isWhitespace()) index++
        return line.getOrNull(index)
    }
}

private data class HssLineTokens(
    val spans: List<Pair<String, SpanStyle>>,
    val inStyleBlock: Boolean,
    val inBlockComment: Boolean,
)

private enum class HssCompletionKind {
    SELECTOR_TYPE,
    STATE,
    PROPERTY,
    VALUE,
}

private data class HssCompletionContext(
    val kind: HssCompletionKind,
    val prefix: String,
    val property: String = "",
) {
    companion object {
        fun from(text: String, offset: Int): HssCompletionContext? {
            val safeOffset = offset.coerceIn(0, text.length)
            val before = text.substring(0, safeOffset)
            val lastOpen = before.lastIndexOf('{')
            val lastClose = before.lastIndexOf('}')
            val prefix = before.takeLastWhile { it.isLetterOrDigit() || it == '-' || it == '_' }
            if (lastOpen > lastClose) {
                val lastColon = before.lastIndexOf(':')
                val lastSemicolon = before.lastIndexOf(';')
                val lastNewLine = before.lastIndexOf('\n')
                val declarationStart = maxOf(lastOpen, lastSemicolon, lastNewLine)
                if (lastColon > declarationStart) {
                    val property = before.substring(declarationStart + 1, lastColon).trim()
                    val valuePrefix = before.substring(lastColon + 1).takeLastWhile { isValuePrefixChar(it) }
                    if (UiLanguageCatalog.valuesFor(property).isEmpty()) return null
                    return HssCompletionContext(HssCompletionKind.VALUE, valuePrefix, property)
                }
                if (prefix.isEmpty()) return null
                return HssCompletionContext(HssCompletionKind.PROPERTY, prefix)
            }
            if (before.endsWith(":$prefix")) return HssCompletionContext(HssCompletionKind.STATE, prefix)
            if (prefix.isEmpty()) return null
            if (before.takeLast(prefix.length + 1).startsWith(".")) return null
            if (before.takeLast(prefix.length + 1).startsWith("#")) return null
            return HssCompletionContext(HssCompletionKind.SELECTOR_TYPE, prefix)
        }

        private fun isValuePrefixChar(char: Char): Boolean {
            return char.isLetterOrDigit() || char in "-_#.%/"
        }
    }
}
