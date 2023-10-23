package ru.hollowhorizon.hollowengine.common.events

import dev.ftb.mods.ftbteams.api.Team
import dev.ftb.mods.ftbteams.api.event.TeamEvent
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.entity.player.Player
import net.minecraftforge.event.TickEvent
import net.minecraftforge.event.TickEvent.ServerTickEvent
import net.minecraftforge.event.entity.player.PlayerEvent
import net.minecraftforge.event.server.ServerStoppingEvent
import net.minecraftforge.event.world.WorldEvent
import net.minecraftforge.server.ServerLifecycleHooks
import ru.hollowhorizon.hc.api.utils.HollowConfig
import ru.hollowhorizon.hc.client.utils.isLogicalClient
import ru.hollowhorizon.hc.common.capabilities.CapabilityInstance
import ru.hollowhorizon.hc.common.capabilities.CapabilityStorage
import ru.hollowhorizon.hollowengine.common.capabilities.StoryTeamCapability
import ru.hollowhorizon.hollowengine.common.capabilities.storyTeam
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager.fromReadablePath
import ru.hollowhorizon.hollowengine.common.scripting.story.StoryStateMachine
import ru.hollowhorizon.hollowengine.common.scripting.story.runScript

object StoryHandler {
    @JvmField
    @HollowConfig("mmo_mode", description = "In mmo mode all players will be have own team")
    var MMO_MODE = false
    private val events = HashMap<Team, HashMap<String, StoryStateMachine>>()

    @JvmStatic
    fun onPlayerJoin(event: PlayerEvent.PlayerLoggedInEvent) {

    }

    @JvmStatic
    fun onServerTick(event: ServerTickEvent) {
        events.values.forEach { stories ->
            stories.values.removeIf { it.tick(event); it.isEnded }
        }
    }

    @JvmStatic
    fun onServerShutdown(event: ServerStoppingEvent) {
        events.forEach { (team, stories) ->
            val storiesNBT = CompoundTag().apply {
                stories.forEach { (path, story) ->
                    put(path, story.serialize())
                }
            }
            team.extraData.put("hollowengine_stories", storiesNBT)
            team.markDirty()
        }
    }

    @JvmStatic
    fun onWorldSave(event: WorldEvent.Save) {
        events.forEach { (team, stories) ->
            val storiesNBT = CompoundTag().apply {
                stories.forEach { (path, story) ->
                    put(path, story.serialize())
                }
            }
            team.extraData.put("hollowengine_stories", storiesNBT)
            team.markDirty()
        }
    }

    fun addStoryEvent(eventPath: String, event: StoryStateMachine, beingRecompiled: Boolean = false) {
        val stories = events.computeIfAbsent(event.team) { HashMap() }

        stories[eventPath] = event

        val extras = event.team.extraData

        if (!extras.contains("hollowengine_stories") || beingRecompiled) return
        val storiesNBT = extras.getCompound("hollowengine_stories")

        if (!storiesNBT.contains(eventPath)) return
        event.deserialize(storiesNBT.getCompound(eventPath))
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
    fun onPlayerClone(event: PlayerEvent.Clone) {
        event.entity.server?.overworld()
            ?.getCapability(CapabilityStorage.getCapability(StoryTeamCapability::class.java))
            ?.ifPresent { teamCap ->
                val team = teamCap.getOrCreateTeam(event.original)

                team.updatePlayer(event.entity as Player)
            }
    }

    fun onTeamLoaded(event: TeamEvent) {
        val extras = event.team.extraData
        if (!extras.contains("hollowengine_stories") || isLogicalClient) return

        val stories = extras.getCompound("hollowengine_stories")

        stories.allKeys.forEach { story ->
            val file = story.fromReadablePath()

            runScript(ServerLifecycleHooks.getCurrentServer(), event.team, file).start()
        }
    }
}