package ru.hollowhorizon.hollowengine.client.ui.xml

class UiMarkupParser(private val source: String) {
    private var index = 0

    fun parse(): UiMarkupDocument {
        val nodes = mutableListOf<UiMarkupNode>()
        while (!isEnd()) {
            skipWhitespace()
            if (isEnd()) break
            nodes += parseNode()
        }
        return UiMarkupDocument(nodes)
    }

    private fun parseNode(): UiMarkupNode {
        if (peek() != '<') return parseText()
        if (source.startsWith("<!--", index)) {
            skipComment()
            skipWhitespace()
            return parseNode()
        }
        if (source.startsWith("</", index)) {
            throw error("Unexpected closing tag")
        }
        return parseElement()
    }

    private fun parseElement(): UiMarkupElement {
        expect('<')
        val name = parseName()
        val attributes = linkedMapOf<String, String>()
        while (!isEnd()) {
            skipWhitespace()
            when {
                source.startsWith("/>", index) -> {
                    index += 2
                    return UiMarkupElement(name, attributes)
                }

                peek() == '>' -> {
                    index++
                    return UiMarkupElement(name, attributes, parseChildren(name))
                }

                else -> {
                    val key = parseName()
                    skipWhitespace()
                    val value = if (peek() == '=') {
                        index++
                        skipWhitespace()
                        parseAttributeValue()
                    } else {
                        "true"
                    }
                    attributes[key] = value
                }
            }
        }
        throw error("Unclosed tag '$name'")
    }

    private fun parseChildren(parentName: String): List<UiMarkupNode> {
        val children = mutableListOf<UiMarkupNode>()
        while (!isEnd()) {
            if (source.startsWith("</", index)) {
                index += 2
                val closingName = parseName()
                skipWhitespace()
                expect('>')
                if (closingName != parentName) throw error("Expected closing tag '$parentName', got '$closingName'")
                return children
            }
            children += parseNode()
        }
        throw error("Unclosed tag '$parentName'")
    }

    private fun parseText(): UiMarkupText {
        val start = index
        while (!isEnd() && peek() != '<') index++
        return UiMarkupText(source.substring(start, index))
    }

    private fun parseName(): String {
        skipWhitespace()
        val start = index
        while (!isEnd()) {
            val char = peek()
            if (!char.isLetterOrDigit() && char != '_' && char != '-' && char != ':' && char != '.') break
            index++
        }
        if (start == index) throw error("Expected name")
        return source.substring(start, index)
    }

    private fun parseAttributeValue(): String {
        val quote = peek()
        if (quote == '"' || quote == '\'') {
            index++
            val result = StringBuilder()
            while (!isEnd()) {
                val char = source[index++]
                if (char == quote) return result.toString()
                if (char == '\\' && !isEnd()) {
                    result.append(source[index++])
                } else {
                    result.append(char)
                }
            }
            throw error("Unclosed attribute value")
        }
        val start = index
        while (!isEnd() && !peek().isWhitespace() && peek() != '>' && !source.startsWith("/>", index)) index++
        return source.substring(start, index)
    }

    private fun skipComment() {
        val close = source.indexOf("-->", index + 4)
        if (close < 0) throw error("Unclosed comment")
        index = close + 3
    }

    private fun skipWhitespace() {
        while (!isEnd() && peek().isWhitespace()) index++
    }

    private fun expect(char: Char) {
        if (peek() != char) throw error("Expected '$char'")
        index++
    }

    private fun peek(): Char = source.getOrNull(index) ?: '\u0000'

    private fun isEnd(): Boolean = index >= source.length

    private fun error(message: String) = UiMarkupParseException(message, index)
}

fun parseUiMarkup(source: String): UiMarkupDocument = UiMarkupParser(source).parse()
