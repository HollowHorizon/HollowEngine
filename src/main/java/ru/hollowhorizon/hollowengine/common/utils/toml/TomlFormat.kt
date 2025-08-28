package ru.hollowhorizon.hollowengine.common.utils.toml

import com.akuleshov7.ktoml.Toml
import com.akuleshov7.ktoml.TomlIndentation
import com.akuleshov7.ktoml.TomlInputConfig
import com.akuleshov7.ktoml.TomlOutputConfig
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.serializer
import ru.hollowhorizon.hollowengine.common.utils.serialization.Format

val toml = Toml(
    inputConfig = TomlInputConfig(
        ignoreUnknownNames = false,
        allowEmptyValues = true,
        allowNullValues = true,
        allowEscapedQuotesInLiteralStrings = true,
        allowEmptyToml = true,
    ),
    outputConfig = TomlOutputConfig(
        indentation = TomlIndentation.FOUR_SPACES,
    )
)

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