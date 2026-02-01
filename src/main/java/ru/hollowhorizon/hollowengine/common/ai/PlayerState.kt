package ru.hollowhorizon.hollowengine.common.ai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PlayerState(
    val name: String,
    val reputation: Int,
    val inventory: List<String>,
    val activeQuests: List<String>
)

@Serializable
data class NpcResponse(
    val decision: String, // allow_entry, deny_entry, ask_question
    @SerialName("dialog_text") val dialogText: String // Сразу текст ответа
)