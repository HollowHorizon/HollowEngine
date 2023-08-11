package ru.hollowhorizon.hollowengine.common.events

import net.minecraft.server.level.ServerPlayer
import net.minecraftforge.event.TickEvent
import net.minecraftforge.event.entity.player.PlayerEvent
import ru.hollowhorizon.hc.api.utils.HollowConfig
import ru.hollowhorizon.hc.common.capabilities.HollowCapabilityV2
import ru.hollowhorizon.hc.common.capabilities.syncWorld
import ru.hollowhorizon.hollowengine.common.capabilities.StoryTeamCapability
import ru.hollowhorizon.hollowengine.common.capabilities.storyTeam
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager
import ru.hollowhorizon.hollowengine.common.scripting.story.StoryExecutorThread
import ru.hollowhorizon.hollowengine.common.scripting.story.StoryTeam

object StoryHandler {
    @JvmField
    @HollowConfig("only_one_team", description = "All players will be in one team")
    var shouldAddToHostTeam = true

    @JvmStatic
    fun onPlayerJoin(event: PlayerEvent.PlayerLoggedInEvent) {
        event.entity.commandSenderWorld.getCapability(HollowCapabilityV2.get(StoryTeamCapability::class.java)).ifPresent {
            val team = it.getTeam(event.entity)
            runAllPossible(team)
        }
    }

    @JvmStatic
    fun onPlayerTick(event: TickEvent.PlayerTickEvent) {
        val team = event.player.storyTeam()
        if (!event.player.level.isClientSide && team.progressManager.shouldUpdate) {
            team.progressManager.shouldUpdate = false
            event.player.level.getCapability(HollowCapabilityV2.get(StoryTeamCapability::class.java))
                .ifPresent { cap -> team.forAllOnline { cap.syncWorld(it.mcPlayer!! as ServerPlayer) } }
        }
    }

    @JvmStatic
    fun runAllPossible(team: StoryTeam) {
        DirectoryManager.getAllStoryEvents().forEach { script ->
            try {
                StoryExecutorThread(team, script).start()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    @JvmStatic
    fun onPlayerClone(event: PlayerEvent.Clone) {
        event.entity.server?.overworld()?.getCapability(HollowCapabilityV2.get(StoryTeamCapability::class.java))
            ?.ifPresent { teamCap ->
                val team = teamCap.getTeam(event.original)

                team.updatePlayer(event.entity)
            }
    }
}