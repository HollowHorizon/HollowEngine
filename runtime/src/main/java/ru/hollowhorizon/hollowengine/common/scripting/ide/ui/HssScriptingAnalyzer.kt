package ru.hollowhorizon.hollowengine.common.scripting.ide.ui

import ru.hollowhorizon.hollowengine.client.ui.hss.HssParseException
import ru.hollowhorizon.hollowengine.client.ui.hss.parseHss
import ru.hollowhorizon.hollowengine.common.scripting.ide.CompletionItem
import ru.hollowhorizon.hollowengine.common.scripting.ide.CompletionItemTag
import ru.hollowhorizon.hollowengine.common.scripting.ide.Diagnostic
import ru.hollowhorizon.hollowengine.common.scripting.ide.Position
import ru.hollowhorizon.hollowengine.common.scripting.ide.Range
import ru.hollowhorizon.hollowengine.common.scripting.ide.ScriptingAnalyzer
import ru.hollowhorizon.hollowengine.common.scripting.ide.Severity
import ru.hollowhorizon.hollowengine.common.scripting.ide.SpanStyle
import ru.hollowhorizon.hollowengine.common.scripting.ide.TextLine
import ru.hollowhorizon.hollowengine.common.scripting.ide.TokenType
import ru.hollowhorizon.hollowengine.common.scripting.ide.declarationCompletionItem

object HssScriptingAnalyzer : ScriptingAnalyzer {
    private val defaultStyle = SpanStyle(TokenType.DEFAULT, italic = false, bold = false, highlight = false)
    private val selectorStyle = SpanStyle(TokenType.CLASS, italic = false, bold = false, highlight = false)
    private val propertyStyle = SpanStyle(TokenType.PROPERTY_IDENTIFIER, italic = false, bold = false, highlight = false)
    private val stringStyle = SpanStyle(TokenType.STRING, italic = false, bold = false, highlight = false)
    private val numberStyle = SpanStyle(TokenType.NUMERIC_LITERAL, italic = false, bold = false, highlight = false)
    private val commentStyle = SpanStyle(TokenType.COMMENT, italic = true, bold = false, highlight = false)

    override fun highlight(name: String, text: String, offset: Int): List<TextLine> {
        var inBlock = false
        return text.lines().map { line ->
            val result = tokenizeLine(line, inBlock)
            inBlock = result.inBlock
            TextLine(result.spans, ArrayList())
        }
    }

    override fun lightweightHighlightLine(name: String, line: String): TextLine {
        return TextLine(tokenizeLine(line, false).spans, ArrayList())
    }

    override fun completions(name: String, text: String, offset: Int): List<CompletionItem> {
        val context = HssCompletionContext.from(text, offset) ?: return emptyList()
        val values = when (context.kind) {
            HssCompletionKind.SELECTOR_TYPE -> UiLanguageCatalog.elementTypes
            HssCompletionKind.STATE -> UiLanguageCatalog.states
            HssCompletionKind.PROPERTY -> UiLanguageCatalog.hssProperties
        }
        return values.asSequence()
            .filter { it.startsWith(context.prefix, ignoreCase = true) }
            .map { completion(it, if (context.kind == HssCompletionKind.PROPERTY) "$it: " else it) }
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

    private fun tokenizeLine(line: String, initialBlock: Boolean): HssLineTokens {
        val spans = mutableListOf<Pair<String, SpanStyle>>()
        var index = 0
        var inBlock = initialBlock
        while (index < line.length) {
            when {
                line.startsWith("/*", index) -> {
                    val close = line.indexOf("*/", index + 2)
                    val end = if (close < 0) line.length else close + 2
                    spans += line.substring(index, end) to commentStyle
                    index = end
                }

                line[index] == '{' -> {
                    spans += "{" to defaultStyle
                    index++
                    inBlock = true
                }

                line[index] == '}' -> {
                    spans += "}" to defaultStyle
                    index++
                    inBlock = false
                }

                line[index] == '"' || line[index] == '\'' -> {
                    val quote = line[index++]
                    val start = index - 1
                    while (index < line.length && line[index] != quote) index++
                    if (index < line.length) index++
                    spans += line.substring(start, index) to stringStyle
                }

                line[index].isDigit() || line[index] == '#' -> {
                    val start = index++
                    while (index < line.length && !line[index].isWhitespace() && line[index] !in ";,(){}") index++
                    spans += line.substring(start, index) to numberStyle
                }

                isIdentifierStart(line[index]) -> {
                    val start = index++
                    while (index < line.length && isIdentifierPart(line[index])) index++
                    val token = line.substring(start, index)
                    val style = if (inBlock && nextNonWhitespace(line, index) == ':') propertyStyle else selectorStyle
                    spans += token to style
                }

                else -> {
                    spans += line[index].toString() to defaultStyle
                    index++
                }
            }
        }
        return HssLineTokens(spans, inBlock)
    }

    private fun completion(show: String, insert: String): CompletionItem {
        return declarationCompletionItem {
            this.show = show
            this.insert = insert
            name = show
            tag = CompletionItemTag.PROPERTY
            fqName = null
            tail = null
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
    val inBlock: Boolean,
)

private enum class HssCompletionKind {
    SELECTOR_TYPE,
    STATE,
    PROPERTY,
}

private data class HssCompletionContext(
    val kind: HssCompletionKind,
    val prefix: String,
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
                if (lastColon > declarationStart) return null
                return HssCompletionContext(HssCompletionKind.PROPERTY, prefix)
            }
            if (before.endsWith(":$prefix")) return HssCompletionContext(HssCompletionKind.STATE, prefix)
            if (before.takeLast(prefix.length + 1).startsWith(".")) return null
            if (before.takeLast(prefix.length + 1).startsWith("#")) return null
            return HssCompletionContext(HssCompletionKind.SELECTOR_TYPE, prefix)
        }
    }
}
