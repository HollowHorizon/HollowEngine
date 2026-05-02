package ru.hollowhorizon.hollowengine.common.utils.toml

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import net.peanuuutz.tomlkt.Toml
import ru.hollowhorizon.hollowengine.common.utils.nbt.TagModule
import ru.hollowhorizon.hollowengine.common.utils.serialization.Format

@PublishedApi
internal val toml = Toml {
    serializersModule = TagModule
    explicitNulls = true
    ignoreUnknownKeys = true
}

object TomlFormat : Format<String> {
    override val serializersModule = toml.serializersModule

    override fun <V> serialize(serializer: SerializationStrategy<V>, value: V): String {
        return toml.encodeToString(serializer, value)
    }

    override fun <V> deserialize(deserializer: DeserializationStrategy<V>, data: String): V {
        return toml.decodeFromString(deserializer, data)
    }

    inline fun <reified T> decodeFromString(string: String): T {
        return toml.decodeFromString(string)
    }

    inline fun <reified T> encodeToString(value: T): String {
        return toml.encodeToString(value)
    }
}