package ru.hollowhorizon.hollowengine.common.ai

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*

@Serializable
sealed class ContentItem {
    @Serializable
    @SerialName("text")
    data class Text(val value: String) : ContentItem()

    @Serializable
    @SerialName("image_url")
    data class ImageUrl(val image_url: Url) : ContentItem()

    @Serializable
    @SerialName("audio_url")
    data class AudioUrl(val audio_url: Url) : ContentItem()

    @Serializable
    data class Url(val url: String)
}

object ContentItemSerializer : KSerializer<Any> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("ContentItem", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Any) {
        when (value) {
            is String -> encoder.encodeString(value.toString())
            is ContentItem.Text -> encoder.encodeString(value.value)
            is ContentItem.ImageUrl -> Json.encodeToJsonElement(value)
                .let { encoder.encodeSerializableValue(JsonElement.serializer(), it) }

            is ContentItem.AudioUrl -> Json.encodeToJsonElement(value)
                .let { encoder.encodeSerializableValue(JsonElement.serializer(), it) }
        }
    }

    override fun deserialize(decoder: Decoder): ContentItem {
        return try {
            val json = decoder.decodeSerializableValue(JsonElement.serializer())
            when {
                json is JsonPrimitive -> ContentItem.Text(json.content)
                (json as? JsonObject)?.containsKey("image_url") == true -> Json.decodeFromJsonElement<ContentItem.ImageUrl>(
                    json
                )

                (json as? JsonObject)?.containsKey("audio_url") == true -> Json.decodeFromJsonElement<ContentItem.AudioUrl>(
                    json
                )

                else -> ContentItem.Text(json.toString())
            }
        } catch (e: Exception) {
            ContentItem.Text(decoder.decodeString())
        }
    }
}

@Serializable
data class Message(
    val role: String = "",
    @Serializable(with = ContentItemSerializer::class)
    val content: Any = "Response error",
) {
    fun getContentText(): String = when (content) {
        is String -> content
        is ContentItem.Text -> content.value
        is List<*> -> content.joinToString("\n") {
            when (it) {
                is ContentItem.Text -> it.value
                else -> it.toString()
            }
        }

        else -> content.toString()
    }
}

@Serializable
data class SimpleChatResponse(
    val role: String,
    val content: String,
)

@Serializable
data class ChatCompletionResponse(
    val id: String = "",
    @SerialName("object")
    val objectType: String = "",
    val created: Long = 0,
    val model: String = "",
    val choices: List<Choice> = emptyList(),
    val usage: Usage = Usage(),
) {
    fun getFirstMessage(): Message? = choices.firstOrNull()?.message
}

@Serializable
data class Choice(
    val index: Int,
    val message: Message,
    val finish_reason: String?,
)

@Serializable
data class Usage(
    val prompt_tokens: Int = -1,
    val completion_tokens: Int = -1,
    val total_tokens: Int = -1,
)

@Serializable
data class Request(val model: String, val messages: List<Message>)

class ShapesIncApi(private val apiKey: String) {
    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
                coerceInputValues = true
            })
        }
        install(Logging) {
            level = LogLevel.HEADERS
        }
    }

    private val baseUrl = "https://api.shapes.inc/v1"

    suspend fun chatCompletions(model: String, messages: List<Message>): Message {
        val request = Request("shapesinc/$model", messages)

        val response = client.post("$baseUrl/chat/completions") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $apiKey")
            setBody(request)
        }

        return try {
            val fullResponse = response.body<ChatCompletionResponse>()
            fullResponse.getFirstMessage() ?: throw IllegalStateException("No message in response")
        } catch (e: Exception) {
            val simpleResponse = response.body<SimpleChatResponse>()
            Message(simpleResponse.role, simpleResponse.content)
        }
    }

    fun close() {
        client.close()
    }
}