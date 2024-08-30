package ru.hollowhorizon.hollowengine.common.npcs.quests

import kotlinx.serialization.Serializable
import net.minecraft.world.entity.player.Player
import ru.hollowhorizon.hc.client.utils.get
import ru.hollowhorizon.hc.common.capabilities.CapabilityInstance
import ru.hollowhorizon.hc.common.capabilities.HollowCapabilityV2
import ru.hollowhorizon.hc.common.network.HollowPacketV2
import ru.hollowhorizon.hc.common.network.HollowPacketV3
import ru.hollowhorizon.hollowengine.common.entities.NPCEntity
import ru.hollowhorizon.hollowengine.common.util.PlayerPermissions

@HollowCapabilityV2(NPCEntity::class)
class QuestsCapability : CapabilityInstance() {
    var questGraph by syncable(QuestGraph())

    override fun canAcceptFromClient(player: Player): Boolean {
        return player.hasPermissions(PlayerPermissions.GAMEMASTER)
    }
}

@HollowCapabilityV2(Player::class)
class AcceptedQuestsCapability : CapabilityInstance() {
    val nodes by syncableList<QuestNode>()
}

@HollowPacketV2(HollowPacketV2.Direction.TO_SERVER)
@Serializable
class AcceptQuestPacket(private val quest: QuestNode) : HollowPacketV3<AcceptQuestPacket> {
    override fun handle(player: Player) {
        val capability = player[AcceptedQuestsCapability::class]
        val nodes = capability.nodes
        if (!nodes.any { it.title == quest.title }) nodes += quest
        capability.synchronize()
    }

}