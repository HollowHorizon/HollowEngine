package ru.hollowhorizon.hollowengine.client.ui.xml

import com.sunnychung.lib.multiplatform.kotlite.error.ParseException
import com.sunnychung.lib.multiplatform.kotlite.katari.KatariParser
import com.sunnychung.lib.multiplatform.kotlite.lexer.Lexer
import com.sunnychung.lib.multiplatform.kotlite.model.ASTNode
import com.sunnychung.lib.multiplatform.kotlite.model.BooleanNode
import com.sunnychung.lib.multiplatform.kotlite.model.CharNode
import com.sunnychung.lib.multiplatform.kotlite.model.DoubleNode
import com.sunnychung.lib.multiplatform.kotlite.model.IntegerNode
import com.sunnychung.lib.multiplatform.kotlite.model.LongNode
import com.sunnychung.lib.multiplatform.kotlite.model.SourcePosition
import com.sunnychung.lib.multiplatform.kotlite.model.StringLiteralNode
import com.sunnychung.lib.multiplatform.kotlite.model.StringNode
import com.sunnychung.lib.multiplatform.kotlite.model.ValueNode
import com.sunnychung.lib.multiplatform.kotlite.model.VariableReferenceNode
import com.sunnychung.lib.multiplatform.kotlite.model.XmlAttributeLiteralNode
import com.sunnychung.lib.multiplatform.kotlite.model.XmlNodeLiteralNode
import com.sunnychung.lib.multiplatform.kotlite.model.XML_TEXT_NODE_NAME
import com.sunnychung.lib.multiplatform.kotlite.model.XML_TEXT_VALUE_ATTRIBUTE

fun parseUiXml(source: String, filename: String = "<ui>"): UiXmlTree {
    val encoded = source.encodeDashedAttributeNames()
    val wrapped = "<$DocumentElementName>\n${encoded.source}\n</$DocumentElementName>"
    val expression = try {
        KatariParser(Lexer(filename, wrapped, isParseSingleQuotedString = true)).expression()
    } catch (exception: ParseException) {
        throw UiXmlParseException(exception.description, exception.position.toUiOffset(source))
    }
    val root = expression as? XmlNodeLiteralNode
        ?: throw UiXmlParseException("UI source must contain XML markup", 0)
    return root.toUiXmlTree(encoded.attributeNames)
}

class UiXmlParseException(
    val messageText: String,
    val position: Int,
) : IllegalArgumentException("$messageText at $position")

private fun XmlNodeLiteralNode.toUiXmlTree(attributeNames: Map<String, String>): UiXmlTree {
    return UiXmlTree(
        name = name,
        attributes = attributes.associate { attributeNames[it.name].orEmpty().ifEmpty { it.name } to it.value.asStaticAttributeValue() },
        children = children.map { child -> child.toUiXmlTreeChild(attributeNames) },
    )
}

private fun ASTNode.toUiXmlTreeChild(attributeNames: Map<String, String>): UiXmlTree {
    return when (this) {
        is XmlNodeLiteralNode -> toUiXmlTree(attributeNames)
        is StringNode -> UiXmlTree(
            name = XML_TEXT_NODE_NAME,
            attributes = mapOf(XML_TEXT_VALUE_ATTRIBUTE to asStaticAttributeValue()),
        )
        else -> throw UiXmlParseException(
            "UI resource XML children must be static text or XML nodes, got ${this::class.simpleName}",
            position.index,
        )
    }
}

private fun ASTNode.asStaticAttributeValue(): String {
    return when (this) {
        is StringNode -> nodes.joinToString("") { it.asStaticAttributeValue() }
        is StringLiteralNode -> content
        is IntegerNode -> value.toString()
        is LongNode -> value.toString()
        is DoubleNode -> value.toString()
        is BooleanNode -> value.toString()
        is CharNode -> value.toString()
        is VariableReferenceNode -> variableName
        is ValueNode -> value.convertToString()
        else -> throw UiXmlParseException(
            "UI resource XML attributes must be static values, got ${this::class.simpleName}",
            position.index,
        )
    }
}

private fun SourcePosition?.toUiOffset(source: String): Int {
    val wrappedIndex = this?.index ?: return 0
    return (wrappedIndex - DocumentPrefixLength).coerceIn(0, source.length)
}

private const val DocumentElementName = "__document"
private val DocumentPrefixLength = "<$DocumentElementName>\n".length

private data class EncodedUiXml(
    val source: String,
    val attributeNames: Map<String, String>,
)

private fun String.encodeDashedAttributeNames(): EncodedUiXml {
    val names = linkedMapOf<String, String>()
    val output = StringBuilder(length)
    var index = 0
    while (index < length) {
        if (this[index] != '<' || getOrNull(index + 1) in setOf('/', '!', '?')) {
            output.append(this[index])
            index++
            continue
        }
        output.append(this[index++])
        while (index < length && !this[index].isWhitespace() && this[index] != '>' && this[index] != '/') {
            output.append(this[index++])
        }
        while (index < length && this[index] != '>') {
            val quote = this[index].takeIf { it == '"' || it == '\'' }
            if (quote != null) {
                val end = stringEnd(index, quote)
                output.append(this, index, end)
                index = end
                continue
            }
            if (!this[index].isAttributeNameStart()) {
                output.append(this[index++])
                continue
            }
            val nameStart = index
            while (index < length && this[index].isAttributeNamePart()) index++
            val name = substring(nameStart, index)
            val afterName = skipWhitespace(index)
            if (afterName < length && this[afterName] == '=' && '-' in name) {
                val encoded = names.entries.firstOrNull { it.value == name }?.key ?: "__hollow_dash_attr_${names.size}".also {
                    names[it] = name
                }
                output.append(encoded)
            } else {
                output.append(name)
            }
        }
        if (index < length) output.append(this[index++])
    }
    return EncodedUiXml(output.toString(), names)
}

private fun String.stringEnd(start: Int, quote: Char): Int {
    var index = start + 1
    while (index < length) {
        if (this[index] == '\\') {
            index += 2
        } else if (this[index] == quote) {
            return index + 1
        } else {
            index++
        }
    }
    return length
}

private fun String.skipWhitespace(start: Int): Int {
    var index = start
    while (index < length && this[index].isWhitespace()) index++
    return index
}

private fun Char.isAttributeNameStart(): Boolean = isLetter() || this == '_'

private fun Char.isAttributeNamePart(): Boolean = isLetterOrDigit() || this == '_' || this == '-' || this == ':' || this == '.'
