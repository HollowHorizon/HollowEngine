package ru.hollowhorizon.hollowengine.common.npcs.dialogues

import kotlinx.serialization.Serializable
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import ru.hollowhorizon.hc.client.utils.nbt.ForEntity
import ru.hollowhorizon.hc.common.network.HollowPacketV2
import ru.hollowhorizon.hc.common.network.HollowPacketV3

@Serializable
class DialogueScene {
    var character = ""
    var text = ""

    val characters = mutableListOf<@Serializable(ForEntity::class) Entity>()
    val choices = mutableListOf<String>()
}

@Serializable
@HollowPacketV2(HollowPacketV2.Direction.TO_CLIENT)
class UpdateScenePacket(val scene: DialogueScene) : HollowPacketV3<UpdateScenePacket> {
    override fun handle(player: Player) {

    }
}