package ru.hollowhorizon.hollowengine.common.utils.yaml

import com.charleskorn.kaml.Yaml
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.modules.SerializersModule

object YamlFormat {
    val yaml = Yaml.default

    fun withModule(serializersModule: SerializersModule): Yaml {
        return Yaml(configuration = yaml.configuration, serializersModule = serializersModule)
    }

    inline fun <reified T> encodeToString(value: T): String {
        return yaml.encodeToString(value)
    }

    fun <T> encodeToString(serializer: SerializationStrategy<T>, value: T, serializersModule: SerializersModule? = null): String {
        val y = if (serializersModule != null) withModule(serializersModule) else yaml
        return y.encodeToString(serializer, value)
    }

    inline fun <reified T> decodeFromString(string: String): T {
        return yaml.decodeFromString(string)
    }

    fun <T> decodeFromString(deserializer: DeserializationStrategy<T>, string: String, serializersModule: SerializersModule? = null): T {
        val y = if (serializersModule != null) withModule(serializersModule) else yaml
        return y.decodeFromString(deserializer, string)
    }
}