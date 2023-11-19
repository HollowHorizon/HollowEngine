package ru.hollowhorizon.hollowengine.common.npcs

import kotlinx.serialization.Serializable
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3
import ru.hollowhorizon.hc.client.utils.nbt.ForBlockPos
import ru.hollowhorizon.hc.client.utils.nbt.NBTFormat
import ru.hollowhorizon.hc.client.utils.nbt.save
import ru.hollowhorizon.hc.client.utils.nbt.serialize
import ru.hollowhorizon.hollowengine.client.utils.ForVec2
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
    var size: Pair<Float, Float> = Pair(0.6f, 1.8f),
    var showName: Boolean = true,
)

@Serializable
data class SpawnLocation(val world: String = "minecraft:overworld", @Serializable(ForBlockPos::class) val pos: BlockPos, @Serializable(ForVec2::class) val rotation: Vec2 = Vec2.ZERO) {
    constructor(world: String = "minecraft:overworld", pos: Vec3, rotation: Vec2 = Vec2.ZERO): this(world, BlockPos(pos), rotation)
}

@Serializable
data class Attributes(val attributes: Map<String, Float> = mapOf()) {
    constructor(vararg attributes: Pair<String, Float>) : this(attributes.toMap())
}
