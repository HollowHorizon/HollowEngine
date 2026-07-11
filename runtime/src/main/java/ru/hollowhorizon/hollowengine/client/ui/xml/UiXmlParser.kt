package ru.hollowhorizon.hollowengine.client.ui.xml

fun parseUiXml(source: String, filename: String = "<ui>"): UiXmlTree {
    return UiXmlParser(source, filename).parse()
}

class UiXmlParseException(
    val messageText: String,
    val position: Int,
) : IllegalArgumentException("$messageText at $position")

private class UiXmlParser(
    private val source: String,
    private val filename: String,
) {
    private var index = 0

    fun parse(): UiXmlTree {
        val children = mutableListOf<UiXmlTree>()
        skipIgnored()
        while (!isEnd()) {
            children += when {
                startsWith("</") -> error("Unexpected closing tag")
                peek() == '<' -> readElement()
                else -> readText()
            }
            skipIgnored()
        }
        return UiXmlTree(DocumentElementName, children = children)
    }

    private fun readElement(): UiXmlTree {
        expect('<')
        if (consume("!")) return readDeclaration()
        if (consume("?")) {
            readProcessingInstruction()
            skipIgnored()
            return readElement()
        }

        val name = readName("Expected element name")
        val attributes = linkedMapOf<String, String>()
        skipWhitespace()
        while (!isEnd() && peek() != '>' && !startsWith("/>")) {
            val attributeName = readName("Expected attribute name")
            skipWhitespace()
            val value = if (consume("=")) {
                skipWhitespace()
                readAttributeValue()
            } else {
                "true"
            }
            attributes[attributeName] = value
            skipWhitespace()
        }

        if (consume("/>")) return UiXmlTree(name, attributes)
        expect('>')

        val children = mutableListOf<UiXmlTree>()
        while (true) {
            if (isEnd()) error("Unclosed element '$name'")
            if (startsWith("</")) {
                index += 2
                val closingName = readName("Expected closing element name")
                if (closingName != name) error("Expected closing tag '$name', got '$closingName'")
                skipWhitespace()
                expect('>')
                return UiXmlTree(name, attributes, children)
            }
            when {
                startsWith("<!--") -> readComment()
                startsWith("<![CDATA[") -> children += readCdata()
                startsWith("<?") -> readProcessingInstruction()
                startsWith("<!") -> readDeclaration()
                peek() == '<' -> children += readElement()
                else -> children += readText()
            }
        }
    }

    private fun readDeclaration(): UiXmlTree {
        val start = index
        when {
            startsWith("--") -> {
                index = start - 2
                readComment()
            }

            startsWith("[CDATA[") -> {
                index = start - 2
                return readCdata()
            }

            else -> {
                val close = source.indexOf('>', index)
                if (close < 0) error("Unclosed declaration")
                index = close + 1
            }
        }
        return UiXmlTree("#text", mapOf("#text" to ""))
    }

    private fun readComment() {
        val close = source.indexOf("-->", index + 4)
        if (close < 0) error("Unclosed comment")
        index = close + 3
    }

    private fun readProcessingInstruction() {
        val close = source.indexOf("?>", index)
        if (close < 0) error("Unclosed processing instruction")
        index = close + 2
    }

    private fun readCdata(): UiXmlTree {
        val start = index + "<![CDATA[".length
        val close = source.indexOf("]]>", start)
        if (close < 0) error("Unclosed CDATA section")
        index = close + 3
        return textNode(source.substring(start, close))
    }

    private fun readText(): UiXmlTree {
        val start = index
        while (!isEnd() && peek() != '<') index++
        return textNode(decodeEntities(source.substring(start, index)))
    }

    private fun readAttributeValue(): String {
        val quote = peek().takeIf { it == '"' || it == '\'' }
        if (quote != null) {
            index++
            val start = index
            while (!isEnd() && peek() != quote) {
                if (peek() == '\\' && index + 1 < source.length) index++
                index++
            }
            if (isEnd()) error("Unclosed attribute string")
            val value = source.substring(start, index)
            index++
            return decodeEntities(value)
        }

        val start = index
        while (!isEnd() && !peek().isWhitespace() && peek() != '>' && !startsWith("/>")) index++
        if (start == index) error("Expected attribute value")
        return decodeEntities(source.substring(start, index))
    }

    private fun readName(message: String): String {
        val start = index
        if (isEnd() || !peek().isNameStart()) error(message)
        index++
        while (!isEnd() && peek().isNamePart()) index++
        return source.substring(start, index)
    }

    private fun skipIgnored() {
        var advanced: Boolean
        do {
            advanced = false
            skipWhitespace().also { advanced = advanced || it }
            if (startsWith("<!--")) {
                readComment()
                advanced = true
            }
            if (startsWith("<?")) {
                readProcessingInstruction()
                advanced = true
            }
        } while (advanced)
    }

    private fun skipWhitespace(): Boolean {
        val start = index
        while (!isEnd() && peek().isWhitespace()) index++
        return index > start
    }

    private fun expect(char: Char) {
        if (isEnd() || peek() != char) error("Expected '$char'")
        index++
    }

    private fun consume(value: String): Boolean {
        if (!startsWith(value)) return false
        index += value.length
        return true
    }

    private fun startsWith(value: String): Boolean = source.startsWith(value, index)

    private fun peek(): Char = source[index]

    private fun isEnd(): Boolean = index >= source.length

    private fun error(message: String): Nothing {
        throw UiXmlParseException("$message in $filename", index.coerceIn(0, source.length))
    }

    private companion object {
        const val DocumentElementName = "__document"
    }
}

private fun textNode(value: String): UiXmlTree {
    return UiXmlTree("#text", mapOf("#text" to value))
}

private fun decodeEntities(value: String): String {
    if ('&' !in value) return value
    val output = StringBuilder(value.length)
    var index = 0
    while (index < value.length) {
        if (value[index] != '&') {
            output.append(value[index++])
            continue
        }
        val semicolon = value.indexOf(';', index + 1)
        if (semicolon < 0) {
            output.append(value[index++])
            continue
        }
        val entity = value.substring(index + 1, semicolon)
        output.append(
            when {
                entity == "lt" -> '<'
                entity == "gt" -> '>'
                entity == "amp" -> '&'
                entity == "quot" -> '"'
                entity == "apos" -> '\''
                entity.startsWith("#x") -> entity.drop(2).toIntOrNull(16)?.toChar() ?: "&$entity;"
                entity.startsWith("#") -> entity.drop(1).toIntOrNull()?.toChar() ?: "&$entity;"
                else -> "&$entity;"
            }
        )
        index = semicolon + 1
    }
    return output.toString()
}

private fun Char.isNameStart(): Boolean = isLetter() || this == '_' || this == ':'

private fun Char.isNamePart(): Boolean = isNameStart() || isDigit() || this == '-' || this == '.'
