package ru.hollowhorizon.hollowengine.common.capabilities

import kotlinx.serialization.Serializable
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.world.World
import ru.hollowhorizon.hc.common.capabilities.HollowCapabilityV2
import ru.hollowhorizon.hc.common.capabilities.HollowCapability
import ru.hollowhorizon.hollowengine.common.events.StoryHandler
import ru.hollowhorizon.hollowengine.common.scripting.story.StoryTeam

@HollowCapabilityV2(World::class)
@Serializable
class StoryTeamCapability : HollowCapability() {
    val teams = HashSet<StoryTeam>()

    fun getTeam(player: PlayerEntity): StoryTeam {
        val team = teams.find { it.isFromTeam(player) } ?:
            if (StoryHandler.shouldAddToHostTeam) {
                teams.firstOrNull() ?: teams.add(StoryTeam()).let { teams.first() }
            } else {
                teams.add(StoryTeam()).let { teams.last() }
            }

        if (player !in team) {
            team.add(player)
        } else {
            if(team.getPlayer(player).mcPlayer == null) {
                team.updatePlayer(player)
            }
        }

        if(team.players.size == 1) team.players.first().isHost = true

        return team

    }
}

fun PlayerEntity.storyTeam(): StoryTeam {
    val cap = this.level.getCapability(HollowCapabilityV2.get<StoryTeamCapability>())
    return cap.orElseThrow { IllegalStateException("No capability found!") }.getTeam(this)
}