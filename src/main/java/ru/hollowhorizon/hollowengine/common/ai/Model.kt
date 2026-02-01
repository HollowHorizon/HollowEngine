package ru.hollowhorizon.hollowengine.common.ai

import de.kherud.llama.InferenceParameters
import de.kherud.llama.LlamaModel
import de.kherud.llama.ModelParameters
import de.kherud.llama.args.LogFormat
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import kotlinx.serialization.json.Json
import net.minecraft.core.BlockPos
import ru.hollowhorizon.hollowengine.common.ai.NpcBrain.chatHistory
import ru.hollowhorizon.hollowengine.common.coroutines.coroutineScope
import ru.hollowhorizon.hollowengine.common.events.SubscribeEvent
import ru.hollowhorizon.hollowengine.common.events.server.ServerChatEvent
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager
import ru.hollowhorizon.hollowengine.common.scripting.story.functions.player.send
import kotlin.io.path.absolutePathString


object NpcBrain {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val modelPath = DirectoryManager.HOLLOW_ENGINE
        .resolve("gemma-3-12b-it-UD-Q5_K_XL.gguf")
        .absolutePathString()

    private val modelParams = ModelParameters()
        .setModel(modelPath)
        .setCtxSize(4096)
        .setGpuLayers(99)

    private val model by lazy {
        LlamaModel(modelParams).also {
            LlamaModel.setLogger(LogFormat.TEXT) { level, msg -> }
        }
    }

    val jsonGrammar = """
        root           ::= "{" space decision-entry "," space dialog-entry "}" space
        
        decision-entry ::= "\"decision\"" space ":" space decision-val
        decision-val   ::= "\"allow_entry\"" | "\"deny_entry\"" | "\"ask_question\""
        
        dialog-entry   ::= "\"dialog_text\"" space ":" space string
        
        string         ::= "\"" ([^"\\] | "\\" .)* "\""
        space          ::= [ \t\n]*
    """.trimIndent()

    fun processInteraction(playerState: PlayerState, chatHistory: List<Pair<String, String>>): NpcResponse {
        val systemPrompt = """
            Ты — стражник у ворот замка.
            
            СОСТОЯНИЕ ИГРОКА (на момент последнего ответа):
            - Имя: ${playerState.name}
            - Репутация: ${playerState.reputation} (Проход от 50)
            - Инвентарь: ${playerState.inventory.joinToString(", ")}
            
            ИНСТРУКЦИЯ:
            Прими решение (decision) и сразу скажи фразу игроку (dialog_text).
            
            ВАРИАНТЫ:
            1. "allowEntry()" — если репутация > 50 и у игрока есть алмазы. Фраза: Добро пожаловать.
            2. "denyEntry()" — если репутация низкая или оружие в руках. Фраза: Грубый отказ с причиной.
            3. "askQuestion()" — если нужно уточнить цель визита.
            Примечание: Ты находишься на стриме популярного блоггера, так что твои ответы должны быть яркими и просто "взрывающими" чат, чтобы за этим было интересно наблюдать. 
            Можешь также грубить и материться, если ситуация позволяет.
            
            Отвечай только JSON.
        """.trimIndent()

        val fullPrompt = StringBuilder()
        fullPrompt.append("<|begin_of_text|><|start_header_id|>system<|end_header_id|>\n\n$systemPrompt<|eot_id|>")

        chatHistory.forEach { (role, content) ->
            fullPrompt.append("<|start_header_id|>$role<|end_header_id|>\n\n$content<|eot_id|>")
        }

        fullPrompt.append("<|start_header_id|>assistant<|end_header_id|>\n\n")

        val params = InferenceParameters(fullPrompt.toString())
            .setTemperature(0.6f)
            .setGrammar(jsonGrammar.replace("\r", ""))
            .setNPredict(256)

        // Получаем и чистим ответ
        val rawJson = model.complete(params).trim()
        return parseJson(rawJson)
    }

    private fun parseJson(rawOutput: String): NpcResponse {
        val startIndex = rawOutput.indexOf('{')
        val endIndex = rawOutput.lastIndexOf('}')

        if (startIndex == -1 || endIndex == -1) {
            return NpcResponse("ask_question", "Что-то я задумался...")
        }

        val jsonString = rawOutput.substring(startIndex, endIndex + 1)
        try {
            return json.decodeFromString(jsonString)
        } catch (e: Exception) {
            println("JSON Parse Error: ${e.message} \nInput: $jsonString")
            return NpcResponse("ask_question", "Не расслышал, повтори.")
        }
    }

    val chatHistory = mutableListOf<Pair<String, String>>()
}

@SubscribeEvent
fun onChatEvent(event: ServerChatEvent) {
    val state = PlayerState(
        event.username, 100,
        event.player.inventory.items.map { itemStack -> itemStack.hoverName.string },
        listOf("Выпустить новый девлог с ИИ квестами")
    )

    chatHistory.add(event.username to event.message.string)

    event.player.server.coroutineScope.launch {
        yield()
        val response = NpcBrain.processInteraction(state, chatHistory)
        event.player.send("[Виталик] ${response.dialogText}")
        chatHistory.add("agent" to response.dialogText)

        when (response.decision) {
            "allow_entry" -> {
                event.player.level().removeBlock(BlockPos(-34, -58, -49), false)
                event.player.level().removeBlock(BlockPos(-33, -58, -49), false)
                event.player.level().removeBlock(BlockPos(-32, -58, -49), false)

                event.player.level().removeBlock(BlockPos(-34, -59, -49), false)
                event.player.level().removeBlock(BlockPos(-33, -59, -49), false)
                event.player.level().removeBlock(BlockPos(-32, -59, -49), false)

                event.player.level().removeBlock(BlockPos(-34, -60, -49), false)
                event.player.level().removeBlock(BlockPos(-33, -60, -49), false)
                event.player.level().removeBlock(BlockPos(-32, -60, -49), false)
            }

            "deny_entry" -> {
                event.player.hurt(event.player.damageSources().generic(), 4f)
            }

            else -> {}
        }
    }
}