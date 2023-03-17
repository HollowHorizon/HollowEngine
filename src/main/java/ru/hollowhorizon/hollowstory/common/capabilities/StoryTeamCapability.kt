package ru.hollowhorizon.hollowstory.common.capabilities

import kotlinx.serialization.Serializable
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.world.World
import ru.hollowhorizon.hc.common.capabilities.HollowCapabilityV2
import ru.hollowhorizon.hc.common.capabilities.IHollowCapability
import ru.hollowhorizon.hollowstory.common.events.StoryHandler
import ru.hollowhorizon.hollowstory.story.StoryTeam

@HollowCapabilityV2(World::class)
@Serializable
class StoryTeamCapability : IHollowCapability {
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

        return team

    }
}

fun PlayerEntity.storyTeam(): StoryTeam {
    val cap = this.level.getCapability(HollowCapabilityV2.get<StoryTeamCapability>())
    return cap.orElseThrow { IllegalStateException("No capability found!") }.getTeam(this)
}