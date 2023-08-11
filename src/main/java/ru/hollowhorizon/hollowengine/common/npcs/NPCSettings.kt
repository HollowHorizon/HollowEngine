package ru.hollowhorizon.hollowengine.common.npcs

import kotlinx.serialization.Serializable
import net.minecraft.sounds.SoundEvent
import ru.hollowhorizon.hc.client.utils.nbt.*
import java.io.File

fun main() {
    val settings = NPCSettings().apply {
        name = "Виталик"

    }

    val nbt = NBTFormat.serialize(settings)
    val file = File("npc.nbt")

    nbt.save(file.outputStream())

}

@Serializable
data class NPCSettings(
    var name: String = "NPC",
    var model: NPCModel = NPCModel(),
    var data: NPCData = NPCData(),
)

@Serializable
data class NPCModel(
    var modelPath: String = "hollowengine:models/entity/player_model.gltf",
    var extraAnimations: String = "hollowengine:models/entity/player_animations.json",
    var textureOverrides: HashMap<String, String> = HashMap()
)

@Serializable
data class NPCData(
    var health: Float = 20f,
    var damage: Float = 1f,
    var armor: Float = 0f,
    var ignoreLighting: Boolean = false,
    var isUndead: Boolean = false,
    var sound: @Serializable(ForSoundEvent::class) SoundEvent? = null,
)