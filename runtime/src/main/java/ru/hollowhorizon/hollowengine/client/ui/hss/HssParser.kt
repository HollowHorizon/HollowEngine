package ru.hollowhorizon.hollowengine.client.ui.hss

import ru.hollowhorizon.hollowengine.client.ui.UiState

class HssParser(private val source: String) {
    private var index = 0
    private var order = 0

    fun parse(): HssDocument {
        val rules = mutableListOf<HssRule>()
        skipIgnored()
        while (!isEnd()) {
            rules += parseRule()
            skipIgnored()
        }
        return HssDocument(rules)
    }

    private fun parseRule(): HssRule {
        val selectors = parseSelectors()
        expect('{')
        val declarations = parseDeclarations()
        expect('}')
        return HssRule(selectors, declarations, order++)
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
        var type: String? = null
        var id: String? = null
        val tags = mutableSetOf<String>()
        val states = mutableSetOf<UiState>()
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
                    val stateName = readIdentifier()
                    states += UiState.fromSelector(stateName)
                        ?: throw HssParseException("Unknown UI state ':$stateName'", index)
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
        return HssSelector(type, id, tags, states)
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
}

fun parseHss(source: String): HssDocument = HssParser(source).parse()
