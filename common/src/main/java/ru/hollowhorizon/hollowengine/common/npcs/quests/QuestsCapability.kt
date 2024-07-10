package ru.hollowhorizon.hollowengine.common.npcs.quests

import net.minecraft.world.entity.player.Player
import ru.hollowhorizon.hc.common.capabilities.CapabilityInstance
import ru.hollowhorizon.hc.common.capabilities.HollowCapabilityV2
import ru.hollowhorizon.hollowengine.common.entities.NPCEntity
import ru.hollowhorizon.hollowengine.common.util.PlayerPermissions

@HollowCapabilityV2(NPCEntity::class)
class QuestsCapability : CapabilityInstance() {
    var questGraph by syncable(QuestGraph())

    override fun canAcceptFromClient(player: Player): Boolean {
        return player.hasPermissions(PlayerPermissions.GAMEMASTER)
    }
}