package ru.hollowhorizon.hollowengine.common.geary.tracking.datastore

import com.mineinabyss.geary.serialization.formats.Format
import kotlinx.io.asInputStream
import kotlinx.io.asOutputStream
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.modules.SerializersModule
import net.minecraft.nbt.Tag
import ru.hollowhorizon.hollowengine.common.utils.nbt.NBTFormat
import ru.hollowhorizon.hollowengine.common.utils.nbt.loadAsNBT
import java.io.DataInputStream
import java.io.DataOutputStream


class GearyNBTFormat(module: SerializersModule) : Format {
    val nbt = NBTFormat(module)
    override val ext: String = "nbt"

    fun <T> encode(serializer: SerializationStrategy<T>, value: T) = nbt.serialize(serializer, value)
    fun <T> decode(deserializer: DeserializationStrategy<T>, value: Tag): T = nbt.deserialize(deserializer, value)

    override fun <T> decode(
        deserializer: DeserializationStrategy<T>,
        source: kotlinx.io.Source,
        overrideSerializersModule: SerializersModule?,
        configType: Format.ConfigType,
    ): T {
        val inputStream = DataInputStream(source.asInputStream())
        return nbt.deserialize(deserializer, inputStream.loadAsNBT())
    }

    override fun <T> encode(
        serializer: SerializationStrategy<T>,
        value: T,
        sink: kotlinx.io.Sink,
        overrideSerializersModule: SerializersModule?,
        configType: Format.ConfigType,
    ) {
        encode(serializer, value).write(DataOutputStream(sink.asOutputStream()))
    }
}