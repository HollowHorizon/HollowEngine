package ru.hollowhorizon.hollowengine.common.events

import dev.ftb.mods.ftbteams.FTBTeamsAPI
import dev.ftb.mods.ftbteams.data.Team
import dev.ftb.mods.ftbteams.event.TeamEvent
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerPlayer
import net.minecraft.stats.Stats
import net.minecraftforge.event.TickEvent.ServerTickEvent
import net.minecraftforge.event.entity.player.PlayerEvent
import net.minecraftforge.event.level.LevelEvent
import net.minecraftforge.event.server.ServerStoppingEvent
import net.minecraftforge.server.ServerLifecycleHooks
import ru.hollowhorizon.hc.api.utils.HollowConfig
import ru.hollowhorizon.hc.client.utils.isLogicalClient
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager.fromReadablePath
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager.toReadablePath
import ru.hollowhorizon.hollowengine.common.scripting.StoryLogger
import ru.hollowhorizon.hollowengine.common.scripting.story.StoryStateMachine
import ru.hollowhorizon.hollowengine.common.scripting.story.runScript

object StoryHandler {
    @JvmField
    @HollowConfig("mmo_mode", description = "In mmo mode all players will be have own team")
    var MMO_MODE = false
    private val events = HashMap<Team, HashMap<String, StoryStateMachine>>()
    private var isStoryPlaying = false
    private val afterTickTasks = ArrayList<Runnable>()
    fun getActiveEvents(team: Team) = events.computeIfAbsent(team) { HashMap() }.keys

    fun stopEvent(team: Team, eventPath: String) {
        events[team]?.remove(eventPath)
    }

    @JvmStatic
    fun onPlayerJoin(event: PlayerEvent.PlayerLoggedInEvent) {
        val player = event.entity as ServerPlayer

        if(player.stats.getValue(Stats.CUSTOM.get(Stats.PLAY_TIME)) == 0) {
            val team = FTBTeamsAPI.getPlayerTeam(player)
            DirectoryManager.firstJoinEvents().forEach { runScript(player.server, team, it) }
        }
    }

    @JvmStatic
    fun onServerTick(event: ServerTickEvent) {
        isStoryPlaying = true
        events.forEach { (team, stories) ->
            stories
                .filter { it.value.tick(event); it.value.isEnded }
                .map { it.key }
                .onEach { StoryLogger.LOGGER.info("Finished event \"{}\", for team \"{}\".", it, team) }
                .forEach(stories::remove)
        }
        isStoryPlaying = false
        afterTickTasks.forEach { it.run() }
        afterTickTasks.clear()
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
        val command = Runnable {

            val stories = events.computeIfAbsent(event.team) { HashMap() }

            val extras = event.team.extraData

            event.isStarted = true
            event.startTasks.forEach { it() }

            if (!extras.contains("hollowengine_stories") || beingRecompiled) {
                stories[eventPath] = event
                return@Runnable
            }
            val storiesNBT = extras.getCompound("hollowengine_stories")

            if (!storiesNBT.contains(eventPath)) {
                stories[eventPath] = event
                return@Runnable
            }
            event.deserialize(storiesNBT.getCompound(eventPath))
            stories[eventPath] = event
        }
        if(isStoryPlaying) afterTickTasks.add(command)
        else command.run()
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