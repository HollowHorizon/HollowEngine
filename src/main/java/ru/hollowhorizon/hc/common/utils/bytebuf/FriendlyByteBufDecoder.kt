package ru.hollowhorizon.hc.common.utils.bytebuf

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.AbstractDecoder
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.modules.SerializersModule
import net.minecraft.network.FriendlyByteBuf

@OptIn(ExperimentalSerializationApi::class)
class FriendlyByteBufDecoder(
    override val serializersModule: SerializersModule,
    private val input: FriendlyByteBuf,
    private var elementsCount: Int = 0,
) : AbstractDecoder() {
    private var elementIndex = 0
    override fun decodeBoolean(): Boolean = input.readBoolean()
    override fun decodeByte(): Byte = input.readByte()
    override fun decodeShort(): Short = input.readShort()
    override fun decodeInt(): Int = input.readInt()
    override fun decodeLong(): Long = input.readLong()
    override fun decodeFloat(): Float = input.readFloat()
    override fun decodeDouble(): Double = input.readDouble()
    override fun decodeChar(): Char = input.readChar()
    override fun decodeString(): String = String(input.readByteArray())
    override fun decodeEnum(enumDescriptor: SerialDescriptor): Int = input.readInt()

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
        if (elementIndex == elementsCount) return CompositeDecoder.DECODE_DONE
        return elementIndex++
    }

    override fun beginStructure(descriptor: SerialDescriptor): CompositeDecoder =
        FriendlyByteBufDecoder(serializersModule, input, descriptor.elementsCount)

    override fun decodeSequentially(): Boolean = true

    override fun decodeCollectionSize(descriptor: SerialDescriptor): Int =
        decodeInt().also { elementsCount = it }

    override fun decodeNotNullMark(): Boolean = decodeBoolean()
}