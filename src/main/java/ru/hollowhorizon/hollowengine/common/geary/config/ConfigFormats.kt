@file:OptIn(ExperimentalSerializationApi::class)

package ru.hollowhorizon.hollowengine.common.geary.config

import com.charleskorn.kaml.*
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.StringFormat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule
import java.io.InputStream
import java.io.OutputStream

open class ConfigFormats(
    overrides: List<Format> = listOf(),
    val serializersModule: SerializersModule = EmptySerializersModule(),
) {
    private val defaultFormats = listOf(
        Format(
            "yml", Yaml(
                serializersModule = serializersModule,
                YamlConfiguration(
                    encodeDefaults = true,
                    strictMode = false,
                    sequenceBlockIndent = 2,
                    singleLineStringStyle = SingleLineStringStyle.PlainExceptAmbiguous
                )
            )
        ),
        Format("json", Json {
            encodeDefaults = true
            ignoreUnknownKeys = true
            prettyPrint = true
            serializersModule = this@ConfigFormats.serializersModule
        })
    )

    val extToFormat = defaultFormats.associateBy { it.ext }
        .plus(overrides.associateBy { it.ext })

    val formats = extToFormat.values.toList()

    fun <T> decode(format: StringFormat, serializer: DeserializationStrategy<T>, input: InputStream): T =
        when (format) {
            is Yaml -> format.decodeFromStream(serializer, input)
            is Json -> format.decodeFromStream(serializer, input)
            else -> format.decodeFromString(serializer, input.bufferedReader().lineSequence().joinToString())
        }

    fun <T> encode(format: StringFormat, serializer: SerializationStrategy<T>, output: OutputStream, data: T) {
        when (format) {
            is Yaml -> format.encodeToStream(serializer, data, output)
            is Json -> format.encodeToStream(serializer, data, output)
            else -> format.encodeToString(serializer, data).byteInputStream().copyTo(output)
        }
    }
}