package ru.hollowhorizon.hollowengine.common.events

import net.minecraft.world.entity.player.Player
import net.minecraftforge.event.TickEvent
import net.minecraftforge.event.entity.player.PlayerEvent
import ru.hollowhorizon.hc.api.utils.HollowConfig
import ru.hollowhorizon.hc.common.capabilities.CapabilityInstance
import ru.hollowhorizon.hc.common.capabilities.CapabilityStorage
import ru.hollowhorizon.hollowengine.common.capabilities.StoryTeamCapability
import ru.hollowhorizon.hollowengine.common.capabilities.storyTeam
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager
import ru.hollowhorizon.hollowengine.common.scripting.story.StoryExecutorThread
import ru.hollowhorizon.hollowengine.common.scripting.story.StoryTeam

object StoryHandler {
    @JvmField
    @HollowConfig("mmo_mode", description = "In mmo mode all players will be have own team")
    var MMO_MODE = false

    @JvmStatic
    fun onPlayerJoin(event: PlayerEvent.PlayerLoggedInEvent) {
        event.entity.commandSenderWorld.getCapability(CapabilityStorage.getCapability(StoryTeamCapability::class.java))
            .ifPresent {
                val team = it.getOrCreateTeam(event.entity as Player)
                runAllPossible(team)
            }
    }

    @JvmStatic
    fun onPlayerTick(event: TickEvent.PlayerTickEvent) {
        val team = event.player.storyTeam()
        if (!event.player.level.isClientSide && team.progressManager.shouldUpdate) {
            team.progressManager.shouldUpdate = false
            event.player.level.getCapability(CapabilityStorage.getCapability(StoryTeamCapability::class.java))
                .ifPresent(CapabilityInstance::sync)
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
        event.entity.server?.overworld()
            ?.getCapability(CapabilityStorage.getCapability(StoryTeamCapability::class.java))
            ?.ifPresent { teamCap ->
                val team = teamCap.getOrCreateTeam(event.original)

                team.updatePlayer(event.entity as Player)
            }
    }
}