package ru.hollowhorizon.hollowengine.common.capabilities

import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import ru.hollowhorizon.hc.common.capabilities.CapabilityInstance
import ru.hollowhorizon.hc.common.capabilities.CapabilityStorage
import ru.hollowhorizon.hc.common.capabilities.HollowCapabilityV2
import ru.hollowhorizon.hollowengine.common.events.StoryHandler
import ru.hollowhorizon.hollowengine.common.scripting.story.StoryTeam

@HollowCapabilityV2(Level::class)
class StoryTeamCapability : CapabilityInstance() {
    val teams by syncableList<StoryTeam>()

    fun getOrCreateTeam(player: Player): StoryTeam {
        val team = teams.find { it.isFromTeam(player) } ?: if (!StoryHandler.MMO_MODE) teams.firstOrNull()
            ?: teams.add(StoryTeam()).let { teams.first() }
        else teams.add(StoryTeam()).let { teams.last() }

        if (player !in team) {
            team.add(player)
            sync()
        } else {
            if (team.getPlayer(player).mcPlayer == null) {
                team.updatePlayer(player)
                sync()
            }
        }

        if (team.players.size == 1 && !team.players.first().isHost) {
            team.players.first().isHost = true
            sync()
        }

        return team
    }
}

fun Player.storyTeam(): StoryTeam {
    val cap = this.level.getCapability(CapabilityStorage.getCapability(StoryTeamCapability::class.java))
    return cap.orElseThrow { IllegalStateException("No capability found!") }.getOrCreateTeam(this)
}