package ru.hollowhorizon.hollowengine.common.npcs.dialogues

import kotlinx.serialization.Serializable
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import ru.hollowhorizon.hc.common.utils.nbt.ForEntity
import ru.hollowhorizon.hc.common.network.HollowPacket
import ru.hollowhorizon.hc.common.network.HollowPacketHandler

@Serializable
class DialogueScene {
    var character = ""
    var text = ""

    val characters = mutableListOf<@Serializable(ForEntity::class) Entity>()
    val choices = mutableListOf<String>()
}

@Serializable
@HollowPacketHandler(HollowPacketHandler.Direction.TO_CLIENT)
class UpdateScenePacket(val scene: DialogueScene) : HollowPacket<UpdateScenePacket> {
    override fun handle(player: Player) {

    }
}