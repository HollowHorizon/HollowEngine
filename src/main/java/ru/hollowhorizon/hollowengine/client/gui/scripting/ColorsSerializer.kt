package ru.hollowhorizon.hollowengine.client.gui.scripting

import de.fabmax.kool.modules.ui2.Colors
import de.fabmax.kool.util.Color
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure
import kotlinx.serialization.serializer
import kotlinx.serialization.builtins.serializer

object ColorSerializer : KSerializer<Color> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Color", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: Color) = encoder.encodeString(value.toHexString())
    override fun deserialize(decoder: Decoder) = Color(decoder.decodeString())
}

object ColorsSerializer : KSerializer<Colors> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("Colors") {
        element("primary", ColorSerializer.descriptor)
        element("primary_variant", ColorSerializer.descriptor)
        element("secondary", ColorSerializer.descriptor)
        element("secondary_variant", ColorSerializer.descriptor)
        element("background", ColorSerializer.descriptor)
        element("background_variant", ColorSerializer.descriptor)
        element("on_primary", ColorSerializer.descriptor)
        element("on_secondary", ColorSerializer.descriptor)
        element("on_background", ColorSerializer.descriptor)
        element("is_light", Boolean.serializer().descriptor)
    }

    override fun serialize(encoder: Encoder, value: Colors) {
        encoder.encodeStructure(descriptor) {
            encodeSerializableElement(descriptor, 0, ColorSerializer, value.primary)
            encodeSerializableElement(descriptor, 1, ColorSerializer, value.primaryVariant)
            encodeSerializableElement(descriptor, 2, ColorSerializer, value.secondary)
            encodeSerializableElement(descriptor, 3, ColorSerializer, value.secondaryVariant)
            encodeSerializableElement(descriptor, 4, ColorSerializer, value.background)
            encodeSerializableElement(descriptor, 5, ColorSerializer, value.backgroundVariant)
            encodeSerializableElement(descriptor, 6, ColorSerializer, value.onPrimary)
            encodeSerializableElement(descriptor, 7, ColorSerializer, value.onSecondary)
            encodeSerializableElement(descriptor, 8, ColorSerializer, value.onBackground)
            encodeBooleanElement(descriptor, 9, value.isLight)
        }
    }

    override fun deserialize(decoder: Decoder): Colors {
        return decoder.decodeStructure(descriptor) {
            var primary: Color? = null
            var primaryVariant: Color? = null
            var secondary: Color? = null
            var secondaryVariant: Color? = null
            var background: Color? = null
            var backgroundVariant: Color? = null
            var onPrimary: Color? = null
            var onSecondary: Color? = null
            var onBackground: Color? = null
            var isLight = false

            while (true) {
                when (val index = decodeElementIndex(descriptor)) {
                    0 -> primary = decodeSerializableElement(descriptor, 0, ColorSerializer)
                    1 -> primaryVariant = decodeSerializableElement(descriptor, 1, ColorSerializer)
                    2 -> secondary = decodeSerializableElement(descriptor, 2, ColorSerializer)
                    3 -> secondaryVariant = decodeSerializableElement(descriptor, 3, ColorSerializer)
                    4 -> background = decodeSerializableElement(descriptor, 4, ColorSerializer)
                    5 -> backgroundVariant = decodeSerializableElement(descriptor, 5, ColorSerializer)
                    6 -> onPrimary = decodeSerializableElement(descriptor, 6, ColorSerializer)
                    7 -> onSecondary = decodeSerializableElement(descriptor, 7, ColorSerializer)
                    8 -> onBackground = decodeSerializableElement(descriptor, 8, ColorSerializer)
                    9 -> isLight = decodeBooleanElement(descriptor, 9)
                    CompositeDecoder.DECODE_DONE -> break
                    else -> error("Unexpected index: $index")
                }
            }

            Colors(
                primary ?: error("primary color not found!"),
                primaryVariant ?: error("primary variant color not found!"),
                secondary ?: error("secondary color not found!"),
                secondaryVariant ?: error("secondary variant color not found!"),
                background ?: error("background color not found!"),
                backgroundVariant ?: error("background variant color not found!"),
                onPrimary ?: error("onPrimary color not found!"),
                onSecondary ?: error("onSecondary color not found!"),
                onBackground ?: error("onBackground color not found!"),
                isLight,
            )
        }
    }
}
