package ru.hollowhorizon.hollowengine.client.ui.style

import ru.hollowhorizon.hollowengine.client.ui.UiState

class HssParser(private val source: String) {
    private var index = 0
    private var order = 0

    fun parse(): HssDocument {
        val rules = mutableListOf<HssRule>()
        val keyframes = mutableListOf<HssKeyframes>()
        skipIgnored()
        while (!isEnd()) {
            if (peek() == '@') {
                keyframes += parseAtRule()
            } else {
                rules += parseRule()
            }
            skipIgnored()
        }
        return HssDocument(rules, keyframes)
    }

    fun parseSelectorOnly(): HssSelector {
        skipIgnored()
        val selector = parseSelector()
        skipIgnored()
        if (!isEnd()) throw HssParseException("Unexpected selector content", index)
        return selector
    }

    private fun parseRule(): HssRule {
        val selectors = parseSelectors()
        expect('{')
        val declarations = parseDeclarations()
        expect('}')
        return HssRule(selectors, declarations, order++)
    }

    private fun parseAtRule(): HssKeyframes {
        expect('@')
        val name = readIdentifier()
        if (!name.equals("keyframes", ignoreCase = true)) throw HssParseException("Unsupported at-rule '@$name'", index)
        skipIgnored()
        val keyframesName = readIdentifier()
        expect('{')
        val frames = mutableListOf<HssKeyframe>()
        skipIgnored()
        while (!isEnd() && peek() != '}') {
            frames += parseKeyframe()
            skipIgnored()
        }
        expect('}')
        return HssKeyframes(keyframesName, frames)
    }

    private fun parseKeyframe(): HssKeyframe {
        val selector = readKeyframeSelector()
        expect('{')
        val declarations = parseDeclarations()
        expect('}')
        val offsets = selector.split(',')
            .map { parseKeyframeOffset(it.trim()) }
            .ifEmpty { throw HssParseException("Expected keyframe selector", index) }
        return HssKeyframe(offsets, declarations)
    }

    private fun readKeyframeSelector(): String {
        skipIgnored()
        val start = index
        while (!isEnd() && peek() != '{') index++
        val selector = source.substring(start, index).trim()
        if (selector.isEmpty()) throw HssParseException("Expected keyframe selector", index)
        return selector
    }

    private fun parseKeyframeOffset(value: String): Float {
        return when {
            value.equals("from", ignoreCase = true) -> 0f
            value.equals("to", ignoreCase = true) -> 1f
            value.endsWith("%") -> value.dropLast(1).toFloat() / 100f
            else -> throw HssParseException("Expected keyframe offset, got '$value'", index)
        }.coerceIn(0f, 1f)
    }

    private fun parseSelectors(): List<HssSelector> {
        val selectors = mutableListOf<HssSelector>()
        while (true) {
            skipIgnored()
            selectors += parseSelector()
            skipIgnored()
            if (peek() != ',') break
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
                    tags += readIdentifier().removePrefix(".")
                    consumed = true
                }

                '#' -> {
                    index++
                    id = readIdentifier().removePrefix("#")
                    consumed = true
                }

                ':' -> {
                    index++
                    states += UiState.of(readIdentifier())
                    consumed = true
                }

                '[' -> {
                    attributes += readAttributeSelector()
                    consumed = true
                }

                '{', ',', ' ', '\n', '\r', '\t' -> break@selector
                else -> {
                    type = readIdentifier()
                    consumed = true
                }
            }
        }
        if (!consumed) throw HssParseException("Expected selector", index)
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
            val property = readPropertyName()
            expect(':')
            val value = readDeclarationValue()
            declarations += HssDeclaration(property, value)
            skipIgnored()
            if (!isEnd() && peek() == ';') {
                index++
                skipIgnored()
            }
        }
        return declarations
    }

    private fun readPropertyName(): String {
        skipIgnored()
        val start = index
        while (!isEnd() && (peek().isLetterOrDigit() || peek() == '-')) index++
        if (start == index) throw HssParseException("Expected declaration property", index)
        skipIgnored()
        return source.substring(start, index)
    }

    private fun readDeclarationValue(): String {
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
        val value = source.substring(start, index).trim()
        if (value.isEmpty()) throw HssParseException("Expected declaration value", index)
        return value
    }

    private fun readIdentifier(): String {
        val start = index
        while (!isEnd() && (peek().isLetterOrDigit() || peek() == '-' || peek() == '_')) index++
        if (start == index) throw HssParseException("Expected identifier", index)
        return source.substring(start, index)
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
        if (start == index) throw HssParseException("Expected attribute name", index)
        return source.substring(start, index)
    }

    private fun readAttributeValue(): String {
        val quote = peek().takeIf { it == '"' || it == '\'' }
        if (quote != null) {
            index++
            val start = index
            while (!isEnd() && (peek() != quote || previous() == '\\')) index++
            if (isEnd()) throw HssParseException("Unclosed attribute selector string", start)
            val value = source.substring(start, index).replace("\\$quote", quote.toString())
            index++
            return value
        }
        val start = index
        while (!isEnd() && !peek().isWhitespace() && peek() != ']') index++
        if (start == index) throw HssParseException("Expected attribute value", index)
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
                if (close < 0) throw HssParseException("Unclosed block comment", index)
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

    private fun expect(char: Char) {
        skipIgnored()
        if (isEnd() || peek() != char) throw HssParseException("Expected '$char'", index)
        index++
    }

    private fun peek(): Char = source[index]

    private fun previous(): Char = if (index == 0) '\u0000' else source[index - 1]

    private fun isEnd() = index >= source.length

    private fun peekAhead(value: String) = source.startsWith(value, index)

    private fun isAttributeNameChar(char: Char): Boolean {
        return char.isLetterOrDigit() || char == '-' || char == '_' || char == ':' || char == '.'
    }
}

fun parseHss(source: String): HssDocument = HssParser(source).parse()

fun parseHssSelector(source: String): HssSelector = HssParser(source).parseSelectorOnly()
