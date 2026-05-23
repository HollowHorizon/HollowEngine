package ru.hollowhorizon.hollowengine.common.scripting.ide.ui

import ru.hollowhorizon.hollowengine.client.ui.xml.UiMarkupParseException
import ru.hollowhorizon.hollowengine.client.ui.xml.parseUiMarkup
import ru.hollowhorizon.hollowengine.common.scripting.ide.*

object UiMarkupScriptingAnalyzer : ScriptingAnalyzer {
    private val defaultStyle = SpanStyle(TokenType.DEFAULT, italic = false, bold = false, highlight = false)
    private val tagStyle = SpanStyle(TokenType.KEYWORD, italic = false, bold = true, highlight = false)
    private val attrStyle = SpanStyle(TokenType.PROPERTY_IDENTIFIER, italic = false, bold = false, highlight = false)
    private val stringStyle = SpanStyle(TokenType.STRING, italic = false, bold = false, highlight = false)
    private val commentStyle = SpanStyle(TokenType.COMMENT, italic = true, bold = false, highlight = false)

    override fun highlight(name: String, text: String, offset: Int): List<TextLine> {
        return text.lines().map { line -> TextLine(tokenizeLine(line), ArrayList()) }
    }

    override fun lightweightHighlightLine(name: String, line: String): TextLine {
        return TextLine(tokenizeLine(line), ArrayList())
    }

    override fun completions(name: String, text: String, offset: Int): List<CompletionItem> {
        val context = UiCompletionContext.from(text, offset) ?: return emptyList()
        return when (context.kind) {
            UiCompletionKind.ELEMENT -> elementCompletions(text, context.prefix)
            UiCompletionKind.ATTRIBUTE -> attributeCompletions(context.elementName, context.prefix)
            UiCompletionKind.ATTRIBUTE_VALUE -> valueCompletions(context.attributeName, context.prefix)
            UiCompletionKind.CLOSING_ELEMENT -> closingElementCompletion(text, offset, context.prefix)
        }
    }

    override fun diagnostic(name: String, text: String): List<Diagnostic> {
        return try {
            parseUiMarkup(text)
            emptyList()
        } catch (exception: UiMarkupParseException) {
            listOf(diagnosticAt(text, exception.position, exception.messageText))
        } catch (exception: IllegalArgumentException) {
            listOf(diagnosticAt(text, 0, exception.message ?: "Invalid UI markup"))
        }
    }

    private fun tokenizeLine(line: String): List<Pair<String, SpanStyle>> {
        val spans = mutableListOf<Pair<String, SpanStyle>>()
        var index = 0

        while (index < line.length) {
            if (line.startsWith("<!--", index)) {
                spans += line.substring(index) to commentStyle
                break
            }

            index = tokenizeTextAndTagStart(line, index, spans)
        }
        return spans
    }

    private fun tokenizeTextAndTagStart(
        line: String,
        startIndex: Int,
        spans: MutableList<Pair<String, SpanStyle>>,
    ): Int {
        var index = startIndex

        while (index < line.length && !line.startsWith("<", index)) {
            index++
        }

        if (index > startIndex) {
            spans += line.substring(startIndex, index) to defaultStyle
        }

        if (index < line.length && line[index] == '<') {
            index = tokenizeTagBody(line, index, spans)
        }

        return index
    }

    private fun tokenizeTagBody(line: String, startIndex: Int, spans: MutableList<Pair<String, SpanStyle>>): Int {
        var index = startIndex

        val nameStart = index++
        if (index < line.length && line[index] == '/') index++
        while (index < line.length && isNameChar(line[index])) index++

        spans += line.substring(nameStart, index) to tagStyle

        while (index < line.length) {
            when {
                line[index] == '>' -> {
                    spans += ">" to tagStyle
                    return index + 1
                }

                line.startsWith("/>", index) -> {
                    spans += "/>" to tagStyle
                    return index + 2
                }

                line[index] == '"' || line[index] == '\'' -> {
                    val quote = line[index]
                    val start = index++
                    while (index < line.length && line[index] != quote) index++
                    if (index < line.length) index++
                    spans += line.substring(start, index) to stringStyle
                }

                isNameStart(line[index]) -> {
                    val start = index++
                    while (index < line.length && isNameChar(line[index])) index++
                    spans += line.substring(start, index) to attrStyle
                }

                else -> {
                    val start = index++
                    while (index < line.length && line[index] != '>' && !line.startsWith(
                            "/>",
                            index
                        ) && line[index] != '"' && line[index] != '\'' && !isNameStart(line[index])
                    ) {
                        index++
                    }
                    spans += line.substring(start, index) to defaultStyle
                }
            }
        }
        return index
    }

    private fun elementCompletions(text: String, prefix: String): List<CompletionItem> {
        val imports = Regex("""<\s*import\b[^>]*(?:named|name)\s*=\s*["']([^"']+)["']""")
            .findAll(text)
            .map { it.groupValues[1] }
        return (UiLanguageCatalog.elementTypes.asSequence() + imports)
            .filter { it.startsWith(prefix, ignoreCase = true) }
            .distinct()
            .map { elementCompletion(it, "$it>") }
            .toList()
    }

    private fun attributeCompletions(elementName: String, prefix: String): List<CompletionItem> {
        return UiLanguageCatalog.attributesFor(elementName)
            .asSequence()
            .filter { it.name.startsWith(prefix, ignoreCase = true) }
            .map { attribute ->
                declarationCompletionItem {
                    show = attribute.name
                    insert = attribute.insertion
                    name = attribute.name
                    tag = CompletionItemTag.PROPERTY
                    moveCaret = attribute.caretMove
                    fqName = null
                    tail = null
                    middle = null
                }
            }
            .toList()
    }

    private fun closingElementCompletion(text: String, offset: Int, prefix: String): List<CompletionItem> {
        val tag = openElementStack(text.substring(0, offset)).lastOrNull()
            ?: return emptyList()
        if (!tag.startsWith(prefix, ignoreCase = true)) return emptyList()
        return listOf(elementCompletion(tag, "$tag>"))
    }

    private fun valueCompletions(attributeName: String, prefix: String): List<CompletionItem> {
        return UiLanguageCatalog.valuesFor(attributeName)
            .asSequence()
            .filter { it.startsWith(prefix, ignoreCase = true) }
            .map { value ->
                declarationCompletionItem {
                    show = value
                    insert = value
                    name = value
                    tag = CompletionItemTag.PROPERTY
                    fqName = null
                    tail = "value"
                    middle = null
                }
            }
            .toList()
    }

    private fun elementCompletion(show: String, insert: String): CompletionItem {
        return declarationCompletionItem {
            this.show = show
            this.insert = insert
            name = show
            tag = CompletionItemTag.CLASS
            fqName = null
            tail = null
            middle = null
        }
    }

    private fun openElementStack(source: String): List<String> {
        val stack = mutableListOf<String>()
        var index = 0
        while (index < source.length) {
            val open = source.indexOf('<', index)
            if (open < 0) break
            if (source.startsWith("<!--", open)) {
                val close = source.indexOf("-->", open + 4)
                index = if (close < 0) source.length else close + 3
                continue
            }
            val close = source.indexOf('>', open + 1)
            if (close < 0) break
            val body = source.substring(open + 1, close).trim()
            when {
                body.startsWith("/") -> {
                    val name = body.drop(1).takeWhile(::isNameChar)
                    if (stack.lastOrNull() == name) stack.removeAt(stack.lastIndex)
                }

                body.startsWith("import") || body.endsWith("/") -> Unit
                else -> stack += body.takeWhile(::isNameChar)
            }
            index = close + 1
        }
        return stack
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

    private fun isNameStart(char: Char): Boolean = char.isLetter() || char == '_' || char == ':' || char == '.'

    private fun isNameChar(char: Char): Boolean = isNameStart(char) || char.isDigit() || char == '-'
}

private enum class UiCompletionKind {
    ELEMENT,
    ATTRIBUTE,
    ATTRIBUTE_VALUE,
    CLOSING_ELEMENT,
}

private data class UiCompletionContext(
    val kind: UiCompletionKind,
    val elementName: String,
    val prefix: String,
    val attributeName: String = "",
) {
    companion object {
        fun from(text: String, offset: Int): UiCompletionContext? {
            val safeOffset = offset.coerceIn(0, text.length)
            val tagStart = text.lastIndexOf('<', safeOffset - 1)
            if (tagStart < 0) return null
            val tagEnd = text.lastIndexOf('>', safeOffset - 1)
            if (tagEnd > tagStart) return null

            val tagText = text.substring(tagStart + 1, safeOffset)
            if (isInsideQuotedAttribute(tagText)) {
                return quotedAttributeContext(tagText)
            }
            if (tagText.startsWith("/")) {
                val prefix = tagText.drop(1).takeLastWhile(::isNameChar)
                return UiCompletionContext(UiCompletionKind.CLOSING_ELEMENT, "", prefix)
            }
            val trimmed = tagText.trimStart()
            val elementName = trimmed.takeWhile(::isNameChar)
            val prefix = trimmed.takeLastWhile(::isNameChar)
            if (elementName.isEmpty() || trimmed == elementName) {
                return UiCompletionContext(UiCompletionKind.ELEMENT, "", prefix)
            }
            val currentToken = trimmed.substringAfterLast(' ').substringAfterLast('\t')
            if ('=' in currentToken || currentToken.startsWith("/")) return null
            if (prefix.isEmpty()) return null
            return UiCompletionContext(UiCompletionKind.ATTRIBUTE, elementName, prefix)
        }

        private fun quotedAttributeContext(tagText: String): UiCompletionContext? {
            val quoteIndex = tagText.indexOfLast { it == '"' || it == '\'' }
            if (quoteIndex < 0) return null
            val beforeQuote = tagText.substring(0, quoteIndex)
            val equals = beforeQuote.lastIndexOf('=')
            if (equals < 0) return null
            val attribute = beforeQuote.substring(0, equals).trimEnd().takeLastWhile(::isNameChar)
            if (attribute.isEmpty()) return null
            val prefix = tagText.substring(quoteIndex + 1).takeLastWhile { isValuePrefixChar(it) }
            if (UiLanguageCatalog.valuesFor(attribute).isEmpty()) return null
            return UiCompletionContext(UiCompletionKind.ATTRIBUTE_VALUE, "", prefix, attribute)
        }

        private fun isInsideQuotedAttribute(tagText: String): Boolean {
            var quote = '\u0000'
            var escaped = false
            for (char in tagText) {
                if (escaped) {
                    escaped = false
                    continue
                }
                if (char == '\\') {
                    escaped = true
                    continue
                }
                if (quote == '\u0000' && (char == '"' || char == '\'')) quote = char
                else if (char == quote) quote = '\u0000'
            }
            return quote != '\u0000'
        }

        private fun isNameChar(char: Char): Boolean =
            char.isLetterOrDigit() || char == '_' || char == '-' || char == ':' || char == '.'

        private fun isValuePrefixChar(char: Char): Boolean {
            return char.isLetterOrDigit() || char in "-_#.%/"
        }
    }
}
