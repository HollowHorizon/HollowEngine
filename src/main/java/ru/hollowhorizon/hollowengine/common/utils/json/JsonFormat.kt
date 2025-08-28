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

package ru.hollowhorizon.hollowengine.common.utils.json

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.modules.SerializersModule
import ru.hollowhorizon.hollowengine.common.utils.serialization.Format

@OptIn(ExperimentalSerializationApi::class)
val json = Json {
    isLenient = true
    ignoreUnknownKeys = true
    allowSpecialFloatingPointValues = true
    useArrayPolymorphism = true
    prettyPrint = true
    prettyPrintIndent = "  "
    allowComments = true
    allowTrailingComma = true
}

object JsonFormat: Format<JsonElement> {
    override val serializersModule = json.serializersModule

    override fun <V> serialize(serializer: SerializationStrategy<V>, value: V): JsonElement {
        return json.encodeToJsonElement(serializer, value)
    }

    override fun <V> deserialize(deserializer: DeserializationStrategy<V>, data: JsonElement): V {
        return json.decodeFromJsonElement(deserializer, data)
    }

    inline fun <reified T> decodeFromString(string: String): T {
        return json.decodeFromString(string)
    }

    fun encodeToString(element: JsonElement): String {
        return json.encodeToString(JsonElement.serializer(), element)
    }

    @OptIn(ExperimentalSerializationApi::class)
    inline fun <reified T> decodeFromStream(stream: java.io.InputStream): T {
        return json.decodeFromStream(stream)
    }

    fun encodeToStream(element: JsonElement, stream: java.io.OutputStream) {
        stream.writer().use {
            it.write(encodeToString(element))
        }
    }
}