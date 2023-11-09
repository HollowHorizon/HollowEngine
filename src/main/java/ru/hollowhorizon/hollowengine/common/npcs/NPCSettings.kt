package ru.hollowhorizon.hollowengine.common.npcs

import kotlinx.serialization.Serializable
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3
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
    var model: String = "hollowengine:models/entity/player_model.gltf",
    var data: Attributes = Attributes(),
    var displayNameInWorld: Boolean = true,
)

data class SpawnLocation(val world: String = "minecraft:overworld", val pos: BlockPos, val rotation: Vec2 = Vec2.ZERO) {
    constructor(world: String = "minecraft:overworld", pos: Vec3, rotation: Vec2 = Vec2.ZERO): this(world, BlockPos(pos), rotation)
}

@Serializable
data class Attributes(val attributes: Map<String, Float> = mapOf()) {
    constructor(vararg attributes: Pair<String, Float>) : this(attributes.toMap())
}
