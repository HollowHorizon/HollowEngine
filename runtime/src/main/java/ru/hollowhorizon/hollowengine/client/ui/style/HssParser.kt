package ru.hollowhorizon.hollowengine.client.ui.style

import ru.hollowhorizon.hollowengine.client.ui.UiState

/** A parsed document together with the errors that were recovered from while reading it. */
data class HssParseResult(
    val document: HssDocument,
    val errors: List<HssParseException>,
)

/**
 * Recursive-descent parser for HSS.
 */
class HssParser(private val source: String) {
    private var index = 0
    private var order = 0
    private var recovering = false
    private val errors = mutableListOf<HssParseException>()

    fun parse(): HssDocument = parseDocument()

    fun parseRecovering(): HssParseResult {
        recovering = true
        val document = parseDocument()
        return HssParseResult(document, errors.toList())
    }

    fun parseSelectorOnly(): HssSelector {
        skipIgnored()
        val selector = parseSelector()
        skipIgnored()
        if (!isEnd()) throw HssParseException("Unexpected selector content", index)
        return selector
    }

    private fun parseDocument(): HssDocument {
        val rules = mutableListOf<HssRule>()
        val keyframes = mutableListOf<HssKeyframes>()
        skipIgnored()
        while (!isEnd()) {
            val start = index
            try {
                if (peek() == '@') keyframes += parseAtRule() else rules += parseRule()
            } catch (exception: HssParseException) {
                if (!recovering) throw exception
                errors += exception
                skipBlock(start)
            }
            if (index == start) index++
            skipIgnored()
        }
        return HssDocument(rules, keyframes)
    }

    private fun parseRule(): HssRule {
        val selectors = parseSelectors()
        expect('{')
        val declarations = parseDeclarations()
        expect('}')
        return HssRule(selectors, declarations, order++)
    }

    private fun parseAtRule(): HssKeyframes {
        val atStart = index
        expect('@')
        val name = readIdentifier()
        if (!name.text.equals("keyframes", ignoreCase = true)) {
            throw HssParseException("Unsupported at-rule '@${name.text}'", atStart, name.end)
        }
        skipIgnored()
        val keyframesName = readIdentifier()
        expect('{')
        val frames = mutableListOf<HssKeyframe>()
        skipIgnored()
        while (!isEnd() && peek() != '}') {
            val start = index
            try {
                frames += parseKeyframe()
            } catch (exception: HssParseException) {
                if (!recovering) throw exception
                errors += exception
                skipBlock(start)
            }
            if (index == start) index++
            skipIgnored()
        }
        expect('}')
        return HssKeyframes(keyframesName.text, frames, keyframesName.start)
    }

    private fun parseKeyframe(): HssKeyframe {
        val selector = readKeyframeSelector()
        expect('{')
        val declarations = parseDeclarations()
        expect('}')
        return HssKeyframe(parseKeyframeOffsets(selector), declarations)
    }

    /** `from`, `to` and percentages, each reported at its own position when malformed. */
    private fun parseKeyframeOffsets(selector: HssToken): List<Float> {
        val offsets = splitValueTokens(selector.text, ',').map { part ->
            parseKeyframeOffset(part.text, selector.start + part.start, selector.start + part.end)
        }
        if (offsets.isEmpty()) throw HssParseException("Expected keyframe selector", selector.start, selector.end)
        return offsets
    }

    private fun readKeyframeSelector(): HssToken {
        skipIgnored()
        val start = index
        while (!isEnd() && peek() != '{') index++
        var end = index
        while (end > start && source[end - 1].isWhitespace()) end--
        if (end == start) throw HssParseException("Expected keyframe selector", start, start + 1)
        return HssToken(source.substring(start, end), start, end)
    }

    private fun parseKeyframeOffset(value: String, start: Int, end: Int): Float {
        return when {
            value.equals("from", ignoreCase = true) -> 0f
            value.equals("to", ignoreCase = true) -> 1f
            // A selector is a percentage of the iteration, so `50%` is offset 0.5, not 50.
            value.endsWith("%") -> (value.dropLast(1).trim().toFloatOrNull()
                ?: throw HssParseException("Expected a keyframe offset, got '$value'", start, end)) / 100f

            else -> throw HssParseException(
                "Expected 'from', 'to' or a percentage, got '$value'",
                start,
                end,
            )
        }.coerceIn(0f, 1f)
    }

    private fun parseSelectors(): List<HssSelector> {
        val selectors = mutableListOf<HssSelector>()
        while (true) {
            skipIgnored()
            selectors += parseSelector()
            skipIgnored()
            if (isEnd() || peek() != ',') break
            index++
        }
        return selectors
    }

    private fun parseSelector(): HssSelector {
        var selector = parseSimpleSelector()
        while (true) {
            val hadWhitespace = consumeSelectorWhitespace()
            if (!hadWhitespace || isEnd() || peek() == '{' || peek() == ',') return selector
            val child = parseSimpleSelector()
            selector = child.copy(ancestor = selector)
        }
    }

    private fun parseSimpleSelector(): HssSelector {
        val start = index
        var type: String? = null
        var id: String? = null
        val tags = mutableSetOf<String>()
        val states = mutableSetOf<UiState>()
        val attributes = mutableSetOf<HssAttributeSelector>()
        var consumed = false
        selector@ while (!isEnd()) {
            when (peek()) {
                '.' -> {
                    index++
                    tags += readIdentifier().text
                    consumed = true
                }

                '#' -> {
                    index++
                    id = readIdentifier().text
                    consumed = true
                }

                ':' -> {
                    index++
                    states += UiState.of(readIdentifier().text)
                    consumed = true
                }

                '[' -> {
                    attributes += readAttributeSelector()
                    consumed = true
                }

                '{', ',', ' ', '\n', '\r', '\t' -> break@selector
                else -> {
                    type = readIdentifier().text
                    consumed = true
                }
            }
        }
        if (!consumed) throw HssParseException("Expected a selector", start, start + 1)
        return HssSelector(type, id, tags, states, attributes)
    }

    private fun consumeSelectorWhitespace(): Boolean {
        val start = index
        while (!isEnd() && peek().isWhitespace()) index++
        return index > start
    }

    private fun parseDeclarations(): List<HssDeclaration> {
        val declarations = mutableListOf<HssDeclaration>()
        skipIgnored()
        while (!isEnd() && peek() != '}') {
            val start = index
            try {
                declarations += parseDeclaration()
            } catch (exception: HssParseException) {
                if (!recovering) throw exception
                errors += exception
                skipDeclaration()
            }
            skipIgnored()
            if (!isEnd() && peek() == ';') {
                index++
                skipIgnored()
            }
            if (index == start) index++
        }
        return declarations
    }

    private fun parseDeclaration(): HssDeclaration {
        val property = readPropertyName()
        expect(':', "Expected ':' after '${property.text}'", property)
        val value = readDeclarationValue(property)
        return HssDeclaration(property.text, value.text, property.start, value.start)
    }

    private fun readPropertyName(): HssToken {
        skipIgnored()
        val start = index
        while (!isEnd() && (peek().isLetterOrDigit() || peek() == '-' || peek() == '_')) index++
        if (start == index) throw HssParseException("Expected a declaration property", start, start + 1)
        return HssToken(source.substring(start, index), start, index)
    }

    private fun readDeclarationValue(property: HssToken): HssToken {
        skipIgnored()
        val start = index
        var depth = 0
        var inString = false
        var quote = '\u0000'
        value@ while (!isEnd()) {
            val char = peek()
            if (inString) {
                if (char == quote && previous() != '\\') inString = false
            } else {
                when (char) {
                    '\'', '"' -> {
                        inString = true
                        quote = char
                    }

                    '(' -> depth++
                    ')' -> depth--
                    ';' -> if (depth == 0) break@value
                    '}' -> if (depth == 0) break@value
                    '/' -> if (depth == 0 && (peekAhead("//") || peekAhead("/*"))) break@value
                }
            }
            index++
        }
        var end = index
        while (end > start && source[end - 1].isWhitespace()) end--
        if (end == start) {
            throw HssParseException("Expected a value for '${property.text}'", property.start, property.end)
        }
        return HssToken(source.substring(start, end), start, end)
    }

    private fun readIdentifier(): HssToken {
        val start = index
        while (!isEnd() && (peek().isLetterOrDigit() || peek() == '-' || peek() == '_')) index++
        if (start == index) throw HssParseException("Expected an identifier", start, start + 1)
        return HssToken(source.substring(start, index), start, index)
    }

    private fun readAttributeSelector(): HssAttributeSelector {
        expect('[')
        skipIgnored()
        val name = readAttributeName()
        skipIgnored()
        val value = if (!isEnd() && peek() == '=') {
            index++
            skipIgnored()
            readAttributeValue()
        } else {
            null
        }
        skipIgnored()
        expect(']')
        return HssAttributeSelector(name, value)
    }

    private fun readAttributeName(): String {
        val start = index
        while (!isEnd() && isAttributeNameChar(peek())) index++
        if (start == index) throw HssParseException("Expected an attribute name", start, start + 1)
        return source.substring(start, index)
    }

    private fun readAttributeValue(): String {
        val quote = peek().takeIf { it == '"' || it == '\'' }
        if (quote != null) {
            index++
            val start = index
            while (!isEnd() && (peek() != quote || previous() == '\\')) index++
            if (isEnd()) throw HssParseException("Unclosed attribute selector string", start, start + 1)
            val value = source.substring(start, index).replace("\\$quote", quote.toString())
            index++
            return value
        }
        val start = index
        while (!isEnd() && !peek().isWhitespace() && peek() != ']') index++
        if (start == index) throw HssParseException("Expected an attribute value", start, start + 1)
        return source.substring(start, index)
    }

    private fun skipIgnored() {
        var advanced: Boolean
        do {
            advanced = false
            while (!isEnd() && peek().isWhitespace()) {
                index++
                advanced = true
            }
            if (peekAhead("/*")) {
                val close = source.indexOf("*/", index + 2)
                if (close < 0) throw HssParseException("Unclosed block comment", index, source.length)
                index = close + 2
                advanced = true
            }
            if (peekAhead("//")) {
                val close = source.indexOf('\n', index + 2)
                index = if (close < 0) source.length else close
                advanced = true
            }
        } while (advanced)
    }

    /** Skips to just past the block that started at [start], so parsing can resume. */
    private fun skipBlock(start: Int) {
        index = index.coerceAtLeast(start)
        var depth = 0
        while (!isEnd()) {
            when (peek()) {
                '{' -> depth++
                '}' -> {
                    index++
                    if (depth <= 1) return
                    depth--
                    continue
                }
            }
            index++
        }
    }

    /** Skips the rest of a broken declaration, stopping before the block's closing brace. */
    private fun skipDeclaration() {
        while (!isEnd() && peek() != ';' && peek() != '}') index++
    }

    private fun expect(char: Char) {
        skipIgnored()
        if (isEnd() || peek() != char) {
            throw HssParseException("Expected '$char'", errorPosition(), errorPosition() + 1)
        }
        index++
    }

    private fun expect(char: Char, message: String, at: HssToken) {
        skipIgnored()
        if (isEnd() || peek() != char) throw HssParseException(message, at.start, at.end)
        index++
    }

    private fun errorPosition(): Int = if (isEnd()) source.length.coerceAtLeast(1) - 1 else index

    private fun peek(): Char = source[index]

    private fun previous(): Char = if (index == 0) '\u0000' else source[index - 1]

    private fun isEnd() = index >= source.length

    private fun peekAhead(value: String) = source.startsWith(value, index)

    private fun isAttributeNameChar(char: Char): Boolean {
        return char.isLetterOrDigit() || char == '-' || char == '_' || char == ':' || char == '.'
    }
}

/** A source slice with its position, used to report errors on the right token. */
internal data class HssToken(val text: String, val start: Int, val end: Int)

fun parseHss(source: String): HssDocument = HssParser(source).parse()

/** Parses [source], recovering from broken rules and declarations instead of giving up. */
fun parseHssRecovering(source: String): HssParseResult = HssParser(source).parseRecovering()

fun parseHssSelector(source: String): HssSelector = HssParser(source).parseSelectorOnly()
