package ru.hollowhorizon.hollowengine.client.gui.scripting

import de.fabmax.kool.util.Color
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.*

object SyntaxHighlight {
    var COMMENT = Color("B0B0B0FF")
    var KEYWORD = Color("CF8E6DFF")
    var STRING = Color("6AAB73FF")
    var PROPERTY_IDENTIFIER = Color("C77DBBFF")
    var EXTENSION_RECEIVER = Color("56A8F5FF")
    var ANNOTATION = Color("B3AE60FF")
    var VALUE_ARGUMENT_NAME = Color("57AAF7FF")
    var NAME_REFERENCE = Color("BCBEC4FF")
    var NUMERIC_LITERAL = Color("2AACB8FF")
    var ERROR_ELEMENT = Color("F75464FF")
    var DEFAULT = Color("FFFFFFFF")
}

object SyntaxHighlightSerializer : KSerializer<SyntaxHighlight> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("SyntaxHighlight") {
        element("COMMENT", ColorSerializer.descriptor)
        element("KEYWORD", ColorSerializer.descriptor)
        element("STRING", ColorSerializer.descriptor)
        element("PROPERTY_IDENTIFIER", ColorSerializer.descriptor)
        element("EXTENSION_RECEIVER", ColorSerializer.descriptor)
        element("ANNOTATION", ColorSerializer.descriptor)
        element("VALUE_ARGUMENT_NAME", ColorSerializer.descriptor)
        element("NAME_REFERENCE", ColorSerializer.descriptor)
        element("NUMERIC_LITERAL", ColorSerializer.descriptor)
        element("ERROR_ELEMENT", ColorSerializer.descriptor)
        element("DEFAULT", ColorSerializer.descriptor)
    }

    override fun serialize(encoder: Encoder, value: SyntaxHighlight) {
        encoder.encodeStructure(descriptor) {
            encodeSerializableElement(descriptor, 0, ColorSerializer, value.COMMENT)
            encodeSerializableElement(descriptor, 1, ColorSerializer, value.KEYWORD)
            encodeSerializableElement(descriptor, 2, ColorSerializer, value.STRING)
            encodeSerializableElement(descriptor, 3, ColorSerializer, value.PROPERTY_IDENTIFIER)
            encodeSerializableElement(descriptor, 4, ColorSerializer, value.EXTENSION_RECEIVER)
            encodeSerializableElement(descriptor, 5, ColorSerializer, value.ANNOTATION)
            encodeSerializableElement(descriptor, 6, ColorSerializer, value.VALUE_ARGUMENT_NAME)
            encodeSerializableElement(descriptor, 7, ColorSerializer, value.NAME_REFERENCE)
            encodeSerializableElement(descriptor, 8, ColorSerializer, value.NUMERIC_LITERAL)
            encodeSerializableElement(descriptor, 9, ColorSerializer, value.ERROR_ELEMENT)
            encodeSerializableElement(descriptor, 10, ColorSerializer, value.DEFAULT)
        }
    }

    override fun deserialize(decoder: Decoder): SyntaxHighlight {
        return decoder.decodeStructure(descriptor) {
            var comment: Color? = null
            var keyword: Color? = null
            var string: Color? = null
            var propertyIdentifier: Color? = null
            var extensionReceiver: Color? = null
            var annotation: Color? = null
            var valueArgumentName: Color? = null
            var nameReference: Color? = null
            var numericLiteral: Color? = null
            var errorElement: Color? = null
            var default: Color? = null

            while (true) {
                when (val index = decodeElementIndex(descriptor)) {
                    0 -> comment = decodeSerializableElement(descriptor, 0, ColorSerializer)
                    1 -> keyword = decodeSerializableElement(descriptor, 1, ColorSerializer)
                    2 -> string = decodeSerializableElement(descriptor, 2, ColorSerializer)
                    3 -> propertyIdentifier = decodeSerializableElement(descriptor, 3, ColorSerializer)
                    4 -> extensionReceiver = decodeSerializableElement(descriptor, 4, ColorSerializer)
                    5 -> annotation = decodeSerializableElement(descriptor, 5, ColorSerializer)
                    6 -> valueArgumentName = decodeSerializableElement(descriptor, 6, ColorSerializer)
                    7 -> nameReference = decodeSerializableElement(descriptor, 7, ColorSerializer)
                    8 -> numericLiteral = decodeSerializableElement(descriptor, 8, ColorSerializer)
                    9 -> errorElement = decodeSerializableElement(descriptor, 9, ColorSerializer)
                    10 -> default = decodeSerializableElement(descriptor, 10, ColorSerializer)
                    CompositeDecoder.DECODE_DONE -> break
                    else -> error("Unexpected index: $index")
                }
            }

            SyntaxHighlight.apply {
                comment?.let { COMMENT = it }
                keyword?.let { KEYWORD = it }
                string?.let { STRING = it }
                propertyIdentifier?.let { PROPERTY_IDENTIFIER = it }
                extensionReceiver?.let { EXTENSION_RECEIVER = it }
                annotation?.let { ANNOTATION = it }
                valueArgumentName?.let { VALUE_ARGUMENT_NAME = it }
                nameReference?.let { NAME_REFERENCE = it }
                numericLiteral?.let { NUMERIC_LITERAL = it }
                errorElement?.let { ERROR_ELEMENT = it }
                default?.let { DEFAULT = it }
            }
        }
    }
}
