/*
 * MIT License
 *
 * Copyright (c) 2024 HollowHorizon
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package ru.hollowhorizon.hollowengine.common.utils.nbt

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.descriptors.PolymorphicKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.encoding.CompositeEncoder
import kotlinx.serialization.modules.SerializersModule
import net.minecraft.nbt.*
import ru.hollowhorizon.hollowengine.mixins.ListTagAccessor

internal fun <T> NBTFormat.writeNbt(value: T, serializer: SerializationStrategy<T>): Tag {
    lateinit var result: Tag

    if (value == null) return EndTag.INSTANCE

    val encoder = NBTWriter(this) { result = it }
    encoder.push("value")
    encoder.encodeSerializableValue(serializer, value)
    encoder.end()
    return (result as CompoundTag).get("value") ?: EndTag.INSTANCE
}

@OptIn(ExperimentalSerializationApi::class)
private sealed class AbstractNBTWriter(
    val format: NBTFormat,
    val nodeConsumer: (Tag) -> Unit,
) : NamedValueTagEncoder() {

    final override val serializersModule: SerializersModule
        get() = format.serializersModule


    private var writePolymorphic = false

    override fun composeName(parentName: String, childName: String): String = childName
    abstract fun putElement(key: String, element: Tag)
    abstract fun getCurrent(): Tag

    override fun encodeTaggedNull(tag: String) = putElement(tag, ByteTag.valueOf(NbtFormatNull))

    override fun encodeTaggedInt(tag: String, value: Int) = putElement(tag, IntTag.valueOf(value))
    override fun encodeTaggedByte(tag: String, value: Byte) = putElement(tag, ByteTag.valueOf(value))
    override fun encodeTaggedShort(tag: String, value: Short) = putElement(tag, ShortTag.valueOf(value))
    override fun encodeTaggedLong(tag: String, value: Long) = putElement(tag, LongTag.valueOf(value))
    override fun encodeTaggedFloat(tag: String, value: Float) = putElement(tag, FloatTag.valueOf(value))
    override fun encodeTaggedDouble(tag: String, value: Double) = putElement(tag, DoubleTag.valueOf(value))
    override fun encodeTaggedBoolean(tag: String, value: Boolean) = putElement(tag, ByteTag.valueOf(value))
    override fun encodeTaggedChar(tag: String, value: Char) = putElement(tag, StringTag.valueOf(value.toString()))
    override fun encodeTaggedString(tag: String, value: String) = putElement(tag, StringTag.valueOf(value))
    override fun encodeTaggedEnum(tag: String, enumDescriptor: SerialDescriptor, ordinal: Int) =
        putElement(tag, StringTag.valueOf(enumDescriptor.getElementName(ordinal)))

    override fun encodeTaggedTag(key: String, tag: Tag) = putElement(key, tag)

    override fun encodeTaggedValue(tag: String, value: Any) {
        putElement(tag, StringTag.valueOf(value.toString()))
    }

    fun push(tag: String) = pushTag(tag)

    override fun elementName(descriptor: SerialDescriptor, index: Int): String {
        return if (descriptor.kind is PolymorphicKind) index.toString() else super.elementName(descriptor, index)
    }

    override fun beginStructure(descriptor: SerialDescriptor): CompositeEncoder {
        val consumer = if (currentTagOrNull == null) nodeConsumer
        else { node -> putElement(currentTag, node) }

        val encoder = when (descriptor.kind) {
            StructureKind.LIST -> {
                if (descriptor.kind == StructureKind.LIST && descriptor.getElementDescriptor(0).isNullable) NullableListEncoder(
                    format,
                    consumer
                )
                else NbtListEncoder(format, consumer)
            }

            is PolymorphicKind -> NbtMapEncoder(format, consumer)
            StructureKind.MAP -> selectMapMode(descriptor,
                ifMap = { NbtMapEncoder(format, consumer) }
            ) { NbtListEncoder(format, consumer) }

            else -> NBTWriter(format, consumer)
        }

        if (writePolymorphic) {
            writePolymorphic = false
            encoder.putElement("type", StringTag.valueOf(descriptor.serialName))
        }

        return encoder
    }

    override fun endEncode(descriptor: SerialDescriptor) {
        nodeConsumer(getCurrent())
    }
}

private open class NBTWriter(format: NBTFormat, nodeConsumer: (Tag) -> Unit) :
    AbstractNBTWriter(format, nodeConsumer) {

    protected val content: CompoundTag = CompoundTag()

    override fun putElement(key: String, element: Tag) {
        content.put(key, element)
    }

    fun end() {
        nodeConsumer(content)
    }

    override fun getCurrent(): Tag = content
}

private class NbtMapEncoder(format: NBTFormat, nodeConsumer: (Tag) -> Unit) : NBTWriter(format, nodeConsumer) {
    private lateinit var key: String

    override fun putElement(key: String, element: Tag) {
        val idx = key.toInt()
        // writing key
        when {
            idx % 2 == 0 -> this.key = when (element) {
                is CompoundTag, is CollectionTag<*>, is EndTag -> throw compoundTagInvalidKeyKind(
                    when (element) {
                        is CompoundTag -> ForCompoundNBT.descriptor
                        is CollectionTag<*> -> ForNbtList.descriptor
                        is EndTag -> ForNbtNull.descriptor
                        else -> error("impossible")
                    }
                )

                else -> element.asString
            }

            else -> content.put(this.key, element)
        }
    }

    override fun getCurrent(): Tag = content

}

private class NullableListEncoder(format: NBTFormat, nodeConsumer: (Tag) -> Unit) : NBTWriter(format, nodeConsumer) {
    override fun putElement(key: String, element: Tag) {
        content.put(key, element)
    }

    override fun getCurrent(): Tag = content

}

private fun ListTag.addAnyTag(index: Int, tag: Tag) {
    (this as? ListTagAccessor)?.list()?.add(index, tag) ?: this.addTag(index, tag)
}

private class NbtListEncoder(json: NBTFormat, nodeConsumer: (Tag) -> Unit) :
    AbstractNBTWriter(json, nodeConsumer) {
    private val list: ListTag = ListTag()

    override fun elementName(descriptor: SerialDescriptor, index: Int): String = index.toString()


    override fun putElement(key: String, element: Tag) {
        val idx = key.toInt()
        list.addAnyTag(idx, element)
    }

    override fun getCurrent(): Tag = list
}