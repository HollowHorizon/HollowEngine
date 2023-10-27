package ru.hollowhorizon.hollowengine.common.events

import dev.ftb.mods.ftbteams.data.Team
import dev.ftb.mods.ftbteams.event.TeamEvent
import net.minecraft.nbt.CompoundTag
import net.minecraftforge.event.TickEvent.ServerTickEvent
import net.minecraftforge.event.entity.player.PlayerEvent
import net.minecraftforge.event.level.LevelEvent
import net.minecraftforge.event.server.ServerStoppingEvent
import net.minecraftforge.server.ServerLifecycleHooks
import ru.hollowhorizon.hc.api.utils.HollowConfig
import ru.hollowhorizon.hc.client.utils.isLogicalClient
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager.fromReadablePath
import ru.hollowhorizon.hollowengine.common.scripting.story.StoryStateMachine
import ru.hollowhorizon.hollowengine.common.scripting.story.runScript

object StoryHandler {
    @JvmField
    @HollowConfig("mmo_mode", description = "In mmo mode all players will be have own team")
    var MMO_MODE = false
    private val events = HashMap<Team, HashMap<String, StoryStateMachine>>()
    fun getActiveEvents(team: Team) = events.computeIfAbsent(team) { HashMap() }.keys

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
            team.save()
        }
    }

    @JvmStatic
    fun onWorldSave(event: LevelEvent.Save) {
        events.forEach { (team, stories) ->
            val storiesNBT = CompoundTag().apply {
                stories.forEach { (path, story) ->
                    put(path, story.serialize())
                }
            }
            team.extraData.put("hollowengine_stories", storiesNBT)
            team.save()
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