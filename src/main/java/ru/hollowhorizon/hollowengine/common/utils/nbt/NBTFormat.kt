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

import com.google.common.reflect.TypeToken
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.modules.*
import net.minecraft.core.BlockPos
import net.minecraft.nbt.*
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream
import kotlin.reflect.KClass

val NBT_TAGS = HashMap<KClass<*>, MutableList<KClass<*>>>()

@OptIn(InternalSerializationApi::class)
@Suppress("UNCHECKED_CAST")
internal val TagModule
    get() = SerializersModule {
        polymorphic(Tag::class) {
            subclass(ByteTag::class, ForByteNBT)
            subclass(ShortTag::class, ForShortNBT)
            subclass(IntTag::class, ForIntNBT)
            subclass(LongTag::class, ForLongNBT)
            subclass(FloatTag::class, ForFloatNBT)
            subclass(DoubleTag::class, ForDoubleNBT)
            subclass(StringTag::class, ForStringNBT)
            subclass(EndTag::class, ForNbtNull)
            subclass(ByteArrayTag::class, ForByteArrayNBT)
            subclass(IntArrayTag::class, ForIntArrayNBT)
            subclass(LongArrayTag::class, ForLongArrayNBT)
            subclass(ListTag::class, ForNbtList)
            subclass(CompoundTag::class, ForCompoundNBT)
        }
        NBT_TAGS.forEach { entry ->
            entry.value.forEach { kClass ->
                polymorphic(entry.key as KClass<Object>, kClass as KClass<Object>, kClass.serializer())
            }
        }
        contextual(ForBlockPos)
        contextual(ForResourceLocation)
        contextual(ForVec3)
        contextual(ForTextComponent)
        contextual(ForItemStack)
        contextual(ForEntity)
        contextual(ForMatrix4f)
        contextual(ForVector3d)
        contextual(ForVector3f)
        contextual(ForUuid)
    }

val MAPPINGS_SERIALIZER by lazy { NBTFormat() }

open class NBTFormat(context: SerializersModule = EmptySerializersModule()) : SerialFormat {
    override val serializersModule = context + TagModule

    companion object Default : NBTFormat() {
        @JvmField
        val LOGGER: Logger = LogManager.getLogger(NBTFormat::class.java)

        init {
            LOGGER.info("Default Serializer loaded!")
        }
    }

    @Serializable
    data class Initializator(val value: String)

    fun init() {
        //Первый вызов сериализатора происходит около 5 секунд, так что лучше сделать это заранее и асинхронно
        runBlocking {
            deserialize<Initializator>(serialize(Initializator("")))
        }
    }

    fun <T> serialize(serializer: SerializationStrategy<T>, obj: T): Tag {
        return writeNbt(obj, serializer)
    }

    fun <T> deserialize(deserializer: DeserializationStrategy<T>, tag: Tag): T {
        return readNbt(tag, deserializer)
    }
}

internal const val NbtFormatNull = 1.toByte()

@OptIn(ExperimentalSerializationApi::class)
internal inline fun <T, R1 : T, R2 : T> selectMapMode(
    mapDescriptor: SerialDescriptor,
    ifMap: () -> R1,
    ifList: () -> R2,
): T {
    val keyDescriptor = mapDescriptor.getElementDescriptor(0)
    val keyKind = keyDescriptor.kind
    return if (keyKind is PrimitiveKind || keyKind == SerialKind.ENUM) {
        ifMap()
    } else {
        ifList()
    }
}

fun Tag.save(stream: DataOutputStream) {
    NbtIo.writeUnnamedTag(this, stream)
}

fun Tag.save(stream: OutputStream) = this.save(DataOutputStream(stream))

fun DataInputStream.loadAsNBT(): Tag {
    return NbtIo.read(this).apply {
        this@loadAsNBT.close()
    }
}

fun InputStream.loadAsNBT() = DataInputStream(this).loadAsNBT()

inline fun <reified T> NBTFormat.serialize(value: T): Tag {
    return serialize(serializersModule.serializer(), value)
}

fun <T : Any> NBTFormat.serializeNoInline(value: T, cl: Class<T>): Tag {
    val typeToken = TypeToken.of(cl)
    return serialize(serializersModule.serializer(typeToken.type), value)
}

inline fun <reified T> NBTFormat.deserialize(tag: Tag): T {
    return deserialize(serializersModule.serializer(), tag)
}

@Suppress("UNCHECKED_CAST")
fun <T : Any> NBTFormat.deserializeNoInline(tag: Tag, cl: Class<out T>): T {
    val typeToken = TypeToken.of(cl)
    return deserialize(serializersModule.serializer(typeToken.type), tag) as T
}

@OptIn(ExperimentalSerializationApi::class)
internal fun compoundTagInvalidKeyKind(keyDescriptor: SerialDescriptor) = IllegalStateException(
    "Value of type ${keyDescriptor.serialName} can't be used in a compound tag as map key. " +
            "It should have either primitive or enum kind, but its kind is ${keyDescriptor.kind}."
)

fun KClass<*>.isSerializable() = annotations.any { it is Serializable }