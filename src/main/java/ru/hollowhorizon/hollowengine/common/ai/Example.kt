package ru.hollowhorizon.hollowengine.common.ai

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import net.minecraft.world.phys.AABB
import ru.hollowhorizon.hc.client.models.internal.manager.AnimatedEntityCapability
import ru.hollowhorizon.hc.client.models.internal.manager.GltfManager
import ru.hollowhorizon.hc.client.utils.get
import ru.hollowhorizon.hc.client.utils.literal
import ru.hollowhorizon.hc.client.utils.rl
import ru.hollowhorizon.hc.common.events.SubscribeEvent
import ru.hollowhorizon.hc.common.events.server.ServerChatEvent
import ru.hollowhorizon.hollowengine.common.entities.NPCEntity
import ru.hollowhorizon.hollowengine.common.scripting.story.functions.npcs.play
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

//@SubscribeEvent
fun onChatEvent(event: ServerChatEvent) {

    thread {
        val response =
            GenerativeAi.answer(event.message.string).choices.firstOrNull()?.message?.content ?: "Мне нечего сказать..."

        event.player.sendSystemMessage("[Виталик] $response".literal)


        event.player.level()
            .getEntitiesOfClass(NPCEntity::class.java, AABB.ofSize(event.player.position(), 15.0, 15.0, 15.0))
            .forEach {
                val model = GltfManager.getOrCreate(it[AnimatedEntityCapability::class.java].model.rl)
                it.play(model.animationPlayer.nameToAnimationMap.keys.random())
            }

    }

}

object GenerativeAi {
    val PORT = "4050"
    val MODEL = "Mistral Instruct"

    private val jsonFormat = Json { ignoreUnknownKeys = true }
    private val messageHistory = mutableListOf<Response.Choice.Message>()

    @OptIn(ExperimentalSerializationApi::class)
    fun answer(request: String): Response {
        // Добавляем пользовательский запрос в историю сообщений
        messageHistory.add(Response.Choice.Message(role = "user", content = request))

        val url = URL("http://localhost:$PORT/v1/chat/completions")
        val json = """
        {
            "model": "$MODEL",
            "messages": ${jsonFormat.encodeToString(messageHistory)},
            "max_tokens": 2048,
            "temperature": 0.7
        }
        """.trimIndent()

        // Открываем соединение
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "application/json")
        connection.doOutput = true

        // Отправляем тело запроса
        connection.outputStream.use { output ->
            OutputStreamWriter(output).use { writer ->
                writer.write(json)
                writer.flush()
            }
        }

        try {
            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                // Декодируем ответ
                val response = jsonFormat.decodeFromStream<Response>(connection.inputStream)

                // Добавляем ответ модели в историю сообщений
                response.choices.firstOrNull()?.message?.let { reply ->
                    messageHistory.add(reply)
                }

                return response
            } else {
                throw IllegalStateException("HTTP $responseCode, ${connection.errorStream.bufferedReader().readText()}")
            }
        } finally {
            connection.disconnect()
        }
    }
}

@Serializable
class Response(
    val choices: List<Choice> = listOf(),
    val created: Int = -1,
    val id: String = "Unknown",
    val model: String = "Unknown",
    @SerialName("object")
    val target: String = "Unknown",
    val usage: Usage = Usage(),
) {
    @Serializable
    class Choice(
        @SerialName("finish_reason")
        val finishReason: String,
        val index: Int,
        val message: Message,
    ) {
        @Serializable
        class Message(
            val content: String,
            val role: String,
        )
    }

    @Serializable
    class Usage(
        @SerialName("completion_tokens")
        val completionTokens: Int = -1,
        @SerialName("prompt_tokens")
        val promptTokens: Int = -1,
        @SerialName("total_tokens")
        val totalTokens: Int = -1,
    )
}
