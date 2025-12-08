package ru.hollowhorizon.hollowengine.common.utils.nbt

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.modules.*
import net.minecraft.nbt.*
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import ru.hollowhorizon.hollowengine.HollowCore
import ru.hollowhorizon.hollowengine.common.utils.serialization.Format
import ru.hollowhorizon.hollowengine.common.utils.serialization.deserialize
import ru.hollowhorizon.hollowengine.common.utils.serialization.serialize
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
        contextual(Number::class, PolymorphicSerializer(Number::class))
        polymorphic(Number::class) {
            subclass(Double::class)
            subclass(Float::class)
            subclass(Int::class)
            subclass(Long::class)
            subclass(Short::class)
            subclass(Byte::class)

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

open class NBTFormat(context: SerializersModule = EmptySerializersModule()) : SerialFormat, Format<Tag> {
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
        // Первый вызов долгий, нужно инициализировать все внутренние механизмы сериализации
        val tag = serialize(Initializator(""))
        NBTFormat.deserialize<Initializator, Tag>(tag)
    }

    override fun <T> serialize(serializer: SerializationStrategy<T>, value: T): Tag {
        return writeNbt(value, serializer)
    }

    override fun <T> deserialize(deserializer: DeserializationStrategy<T>, data: Tag): T {
        return readNbt(data, deserializer)
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
    try {
        return NbtIo.read(this).apply {
            this@loadAsNBT.close()
        }
    } catch (e: Exception) {
        HollowCore.LOGGER.error("Error while reading nbt!", e)
        return CompoundTag()
    }
}

fun InputStream.loadAsNBT() = DataInputStream(this).loadAsNBT()

@OptIn(ExperimentalSerializationApi::class)
internal fun compoundTagInvalidKeyKind(keyDescriptor: SerialDescriptor) = IllegalStateException(
    "Value of type ${keyDescriptor.serialName} can't be used in a compound tag as map key. " +
            "It should have either primitive or enum kind, but its kind is ${keyDescriptor.kind}."
)

fun KClass<*>.isSerializable() = annotations.any { it is Serializable }