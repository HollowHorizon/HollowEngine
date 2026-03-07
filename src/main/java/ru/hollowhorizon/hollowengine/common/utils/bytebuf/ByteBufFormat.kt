package ru.hollowhorizon.hollowengine.common.utils.bytebuf

import com.google.common.reflect.TypeToken
import io.netty.buffer.Unpooled
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerialFormat
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.plus
import kotlinx.serialization.serializer
import net.minecraft.network.FriendlyByteBuf
import ru.hollowhorizon.hollowengine.common.geary.snapshot.EntitySerialization
import ru.hollowhorizon.hollowengine.common.geary.snapshot.EntitySnapshot
import ru.hollowhorizon.hollowengine.common.geary.tracking.MCEntity
import ru.hollowhorizon.hollowengine.common.utils.nbt.TagModule
import ru.hollowhorizon.hollowengine.common.utils.serialization.Format

open class ByteBufFormat(context: SerializersModule = EmptySerializersModule()) : SerialFormat, Format<FriendlyByteBuf> {
    override val serializersModule = context + TagModule

    companion object Default : ByteBufFormat() {
        fun serializeEntity(snapshot: EntitySnapshot, buf: FriendlyByteBuf = FriendlyByteBuf(Unpooled.buffer())): FriendlyByteBuf =
            EntitySerialization.serializeToByteBuf(snapshot, buf)

        fun serializeEntity(entity: MCEntity, buf: FriendlyByteBuf = FriendlyByteBuf(Unpooled.buffer())): FriendlyByteBuf =
            EntitySerialization.serializeEntityToByteBuf(entity, buf)

        fun deserializeEntity(buf: FriendlyByteBuf): EntitySnapshot = EntitySerialization.deserializeFromByteBuf(buf)
        fun deserializeInto(target: MCEntity, buf: FriendlyByteBuf): EntitySnapshot = EntitySerialization.deserializeInto(target, buf)
    }

    fun <T> serialize(
        serializer: SerializationStrategy<T>,
        obj: T,
        buf: FriendlyByteBuf = FriendlyByteBuf(Unpooled.buffer()),
    ): FriendlyByteBuf {
        val encoder = FriendlyByteBufEncoder(serializersModule, buf)
        encoder.encodeSerializableValue(serializer, obj)
        return buf
    }

    override fun <V> serialize(serializer: SerializationStrategy<V>, value: V): FriendlyByteBuf {
        return serialize(serializer, value, FriendlyByteBuf(Unpooled.buffer()))
    }

    override fun <T> deserialize(deserializer: DeserializationStrategy<T>, tag: FriendlyByteBuf): T {
        val decoder = FriendlyByteBufDecoder(serializersModule, tag)
        return decoder.decodeSerializableValue(deserializer)
    }
}

fun <T : Any> ByteBufFormat.serializeNoInline(
    value: T,
    cl: Class<T>,
    buf: FriendlyByteBuf = FriendlyByteBuf(Unpooled.buffer()),
): FriendlyByteBuf {
    val typeToken = TypeToken.of(cl)
    return serialize(serializersModule.serializer(typeToken.type), value, buf)
}

@Suppress("UNCHECKED_CAST")
fun <T : Any> ByteBufFormat.deserializeNoInline(tag: FriendlyByteBuf, cl: Class<out T>): T {
    val typeToken = TypeToken.of(cl)
    return deserialize(serializersModule.serializer(typeToken.type), tag) as T
}
