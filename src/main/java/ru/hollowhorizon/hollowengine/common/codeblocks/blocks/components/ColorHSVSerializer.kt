package ru.hollowhorizon.hollowengine.common.codeblocks.blocks.components

import de.fabmax.kool.util.Color
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object ColorHSVSerializer : KSerializer<ColorHSV> {
    override val descriptor = String.Companion.serializer().descriptor

    override fun serialize(encoder: Encoder, value: ColorHSV) {
        encoder.encodeString(value.toColor().toHexString())
    }

    override fun deserialize(decoder: Decoder): ColorHSV {
        val str = decoder.decodeString()
        val (hsv, a) = Color(str).let { it.toHsv() to it.a }
        return ColorHSV().apply {
            hue.set(hsv.h)
            sat.set(hsv.s)
            value.set(hsv.v)
            alpha.set(a)
            hexString.set(str)
        }
    }
}