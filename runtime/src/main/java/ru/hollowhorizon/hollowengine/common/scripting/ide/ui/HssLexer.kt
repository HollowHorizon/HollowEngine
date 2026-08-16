package ru.hollowhorizon.hollowengine.common.scripting.ide.ui

import ru.hollowhorizon.hollowengine.common.scripting.ide.TokenType

private val HssTaskMarkerRegex = Regex("""\b(TODO|FIXME|HACK|XXX|BUG)\b""")

/** A highlighted slice of an HSS document. Spans cover the source without gaps. */
data class TextSpan(
    val start: Int,
    val end: Int,
    val type: TokenType,
    val italic: Boolean = false,
)

/** Where in the document the scanner currently is; decides how a token is read. */
internal enum class HssRegion {
    SELECTOR,
    KEYFRAME_SELECTOR,
    PROPERTY,
    VALUE,
}

/**
 * Scanner state at a point in the document. Completion asks the lexer to run up to the
 * caret and reads this instead of guessing the context from nearby punctuation.
 */
internal data class HssScanState(
    val region: HssRegion,
    val property: String?,
    val valueStart: Int,
)

/**
 * Scanner behind HSS highlighting. It tracks block depth and `@keyframes` bodies, so a
 * keyframe selector (`from`, `50%`) is never mistaken for a property and the declarations
 * nested two blocks deep keep their colours.
 *
 * Value tokens are classified against [UiLanguageCatalog], which means the keywords a
 * property accepts are highlighted as keywords without any list living here.
 */
internal class HssLexer(
    private val text: String,
    private val keyframeNames: Set<String> = emptySet(),
    initialDepth: Int = 0,
) {
    private var index = 0
    private var depth = initialDepth
    private var keyframesDepth = -1
    private var pendingKeyframes = false
    private var inValue = false
    private var property: String? = null
    private var valueStart = 0
    private val spans = ArrayList<TextSpan>()

    fun tokenize(): List<TextSpan> {
        while (index < text.length) scan()
        return spans
    }

    /** Scans the whole input and reports where the scanner ended up. */
    fun scanState(): HssScanState {
        tokenize()
        return HssScanState(region(), property, valueStart)
    }

    private fun scan() {
        val char = text[index]
        when {
            char.isWhitespace() -> readWhile(TokenType.DEFAULT) { it.isWhitespace() }
            text.startsWith("/*", index) -> readBlockComment()
            text.startsWith("//", index) -> readLineComment()
            char == '{' -> readBrace(open = true)
            char == '}' -> readBrace(open = false)
            char == ';' -> {
                emit(index, index + 1, TokenType.DEFAULT)
                index++
                inValue = false
                property = null
            }

            else -> when (region()) {
                HssRegion.SELECTOR -> readSelectorToken()
                HssRegion.KEYFRAME_SELECTOR -> readKeyframeSelectorToken()
                HssRegion.PROPERTY -> readPropertyToken()
                HssRegion.VALUE -> readValueToken()
            }
        }
    }

    private fun region(): HssRegion = when {
        depth == 0 -> HssRegion.SELECTOR
        depth == keyframesDepth -> HssRegion.KEYFRAME_SELECTOR
        inValue -> HssRegion.VALUE
        else -> HssRegion.PROPERTY
    }

    private fun readBrace(open: Boolean) {
        emit(index, index + 1, TokenType.DEFAULT)
        index++
        inValue = false
        property = null
        if (open) {
            depth++
            if (pendingKeyframes) {
                keyframesDepth = depth
                pendingKeyframes = false
            }
        } else {
            if (depth == keyframesDepth) keyframesDepth = -1
            depth = (depth - 1).coerceAtLeast(0)
        }
    }

    private fun readBlockComment() {
        val close = text.indexOf("*/", index + 2)
        val end = if (close < 0) text.length else close + 2
        emitComment(index, end)
        index = end
    }

    private fun readLineComment() {
        val close = text.indexOf('\n', index)
        val end = if (close < 0) text.length else close
        emitComment(index, end)
        index = end
    }

    private fun emitComment(start: Int, end: Int) {
        val marker = HssTaskMarkerRegex.find(text.substring(start, end))
        if (marker == null) {
            emit(start, end, TokenType.COMMENT, italic = true)
            return
        }
        val markerStart = start + marker.range.first
        emit(start, markerStart, TokenType.COMMENT, italic = true)
        emit(markerStart, end, TokenType.TODO_COMMENT, italic = true)
    }

    private fun readSelectorToken() {
        when (val char = text[index]) {
            '@' -> readAtRule()
            '.' -> readPrefixed(TokenType.FUNCTION)
            '#' -> readPrefixed(TokenType.EXTENSION_RECEIVER)
            ':' -> readPrefixed(TokenType.ANNOTATION)
            '[' -> readAttributeSelector()
            '"', '\'' -> readString()
            else -> if (isIdentifierPart(char)) {
                readWhile(TokenType.CLASS) { isIdentifierPart(it) }
            } else {
                readSingle(TokenType.DEFAULT)
            }
        }
    }

    private fun readAtRule() {
        val start = index
        index++
        while (index < text.length && isIdentifierPart(text[index])) index++
        val name = text.substring(start + 1, index)
        emit(start, index, TokenType.KEYWORD)
        if (!name.equals("keyframes", ignoreCase = true)) return
        pendingKeyframes = true
        skipSpacesAsDefault()
        if (index < text.length && isIdentifierPart(text[index])) readWhile(TokenType.CLASS) { isIdentifierPart(it) }
    }

    private fun readKeyframeSelectorToken() {
        val char = text[index]
        when {
            char.isDigit() || char == '.' || char == '-' -> readWhile(TokenType.NUMERIC_LITERAL) {
                it.isDigit() || it == '.' || it == '%' || it == '-'
            }

            isIdentifierPart(char) -> {
                val start = index
                while (index < text.length && isIdentifierPart(text[index])) index++
                val word = text.substring(start, index)
                val keyword = word.equals("from", ignoreCase = true) || word.equals("to", ignoreCase = true)
                emit(start, index, if (keyword) TokenType.KEYWORD else TokenType.DEFAULT)
            }

            else -> readSingle(TokenType.DEFAULT)
        }
    }

    private fun readPropertyToken() {
        val char = text[index]
        when {
            char == ':' -> {
                emit(index, index + 1, TokenType.DEFAULT)
                index++
                inValue = true
                valueStart = index
            }

            isIdentifierPart(char) -> {
                val start = index
                while (index < text.length && isIdentifierPart(text[index])) index++
                property = text.substring(start, index)
                emit(start, index, TokenType.PROPERTY_IDENTIFIER)
            }

            else -> readSingle(TokenType.DEFAULT)
        }
    }

    private fun readValueToken() {
        val char = text[index]
        when {
            char == '"' || char == '\'' -> readString()
            char == '#' -> readWhile(TokenType.NUMERIC_LITERAL) { it.isLetterOrDigit() || it == '#' }
            char.isDigit() || (char == '-' && text.getOrNull(index + 1)?.isDigit() == true) ->
                readWhile(TokenType.NUMERIC_LITERAL) { it.isDigit() || it == '.' || it == '%' || it.isLetter() }

            isIdentifierPart(char) -> readValueWord()
            else -> readSingle(TokenType.DEFAULT)
        }
    }

    private fun readValueWord() {
        val start = index
        while (index < text.length && isIdentifierPart(text[index])) index++
        val word = text.substring(start, index)
        emit(start, index, valueWordType(word))
    }

    private fun valueWordType(word: String): TokenType {
        if (text.getOrNull(index) == '(') return TokenType.FUNCTION
        if (word in keyframeNames) return TokenType.CLASS
        val declaration = property?.let(UiLanguageCatalog::property) ?: return TokenType.NAME_REFERENCE
        val keywords = declaration.syntax.slots.flatMap { it.keywords }
        return if (word.lowercase() in keywords) TokenType.KEYWORD else TokenType.NAME_REFERENCE
    }

    private fun readAttributeSelector() {
        emit(index, index + 1, TokenType.DEFAULT)
        index++
        while (index < text.length && text[index] != ']') {
            when {
                text[index] == '"' || text[index] == '\'' -> readString()
                text[index] == '=' -> readSingle(TokenType.DEFAULT)
                text[index].isWhitespace() -> readWhile(TokenType.DEFAULT) { it.isWhitespace() }
                else -> readWhile(TokenType.VALUE_ARGUMENT_NAME) { it != ']' && it != '=' && !it.isWhitespace() }
            }
        }
        if (index < text.length) {
            emit(index, index + 1, TokenType.DEFAULT)
            index++
        }
    }

    private fun readString() {
        val quote = text[index]
        val start = index
        index++
        while (index < text.length && (text[index] != quote || text[index - 1] == '\\')) {
            if (text[index] == '\n') break
            index++
        }
        if (index < text.length && text[index] == quote) index++
        emit(start, index, TokenType.STRING)
    }

    private fun readPrefixed(type: TokenType) {
        val start = index
        index++
        while (index < text.length && isIdentifierPart(text[index])) index++
        emit(start, index, type)
    }

    private fun readSingle(type: TokenType) {
        emit(index, index + 1, type)
        index++
    }

    private inline fun readWhile(type: TokenType, predicate: (Char) -> Boolean) {
        val start = index
        while (index < text.length && predicate(text[index])) index++
        if (index == start) index++
        emit(start, index, type)
    }

    private fun skipSpacesAsDefault() {
        if (index < text.length && text[index].isWhitespace()) {
            readWhile(TokenType.DEFAULT) { it.isWhitespace() && it != '\n' }
        }
    }

    private fun emit(start: Int, end: Int, type: TokenType, italic: Boolean = false) {
        if (end > start) spans += TextSpan(start, end, type, italic)
    }

    private fun isIdentifierPart(char: Char): Boolean =
        char.isLetterOrDigit() || char == '-' || char == '_'
}
