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
    val wrapped = "<$DocumentElementName>\n$source\n</$DocumentElementName>"
    val expression = try {
        KatariParser(Lexer(filename, wrapped, isParseSingleQuotedString = true)).expression()
    } catch (exception: ParseException) {
        throw UiXmlParseException(exception.description, exception.position.toUiOffset(source))
    }
    val root = expression as? XmlNodeLiteralNode
        ?: throw UiXmlParseException("UI source must contain XML markup", 0)
    return root.toUiXmlTree()
}

class UiXmlParseException(
    val messageText: String,
    val position: Int,
) : IllegalArgumentException("$messageText at $position")

private fun XmlNodeLiteralNode.toUiXmlTree(): UiXmlTree {
    return UiXmlTree(
        name = name,
        attributes = attributes.associate { it.name to it.value.asStaticAttributeValue() },
        children = children.map { child -> child.toUiXmlTreeChild() },
    )
}

private fun ASTNode.toUiXmlTreeChild(): UiXmlTree {
    return when (this) {
        is XmlNodeLiteralNode -> toUiXmlTree()
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
