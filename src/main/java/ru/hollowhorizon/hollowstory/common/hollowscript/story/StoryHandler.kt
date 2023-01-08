package ru.hollowhorizon.hollowstory.common.hollowscript.story

import net.minecraftforge.event.entity.player.PlayerEvent
import net.minecraftforge.fml.event.server.FMLServerStartingEvent
import ru.hollowhorizon.hc.api.utils.HollowConfig
import ru.hollowhorizon.hc.client.utils.toRL
import ru.hollowhorizon.hc.client.utils.toSTC
import ru.hollowhorizon.hollowstory.story.StoryPlayer
import ru.hollowhorizon.hollowstory.story.StoryTeam

object StoryHandler {
    @JvmField
    @HollowConfig("should_add_to_host_team", description = "Should add player to host team")
    var shouldAddToHostTeam = true

    @JvmStatic
    fun onServerStart(event: FMLServerStartingEvent) {
        StoryTeamsData.INSTANCE =
            event.server.overworld().chunkSource.dataStorage.computeIfAbsent(::StoryTeamsData, "story_teams")
    }

    @JvmStatic
    fun onPlayerJoin(event: PlayerEvent.PlayerLoggedInEvent) {
        val team = StoryStorage.teams.find { it.isFromTeam(event.player) }
        if (team != null) { //Если игрок уже в команде, то обновляем его данные
            team.getPlayer(event.player).mcPlayer = event.player
        } else if (shouldAddToHostTeam && StoryStorage.teams.size != 0) { //Если игрока нет в команде, но на сервере включен режим хоста, то добавляем его в команду хоста
            if (!StoryStorage.teams[0].hasPlayer(event.player)) StoryStorage.teams[0].players.add(StoryPlayer(event.player))
        } else { //Если игрока нет в команде и режим хоста выключен, то создаём ему новую команду
            StoryStorage.teams.add(StoryTeam().apply {
                players.add(StoryPlayer(event.player))
            })
        }

        event.player.sendMessage("[§6HollowStory] Начинается компиляция сюжета".toSTC(), event.player.uuid)
        try {
            executeStory(event.player, "hollowstory:hevents/bandits.se.kts".toRL())
            executeStory(event.player, "hollowstory:hevents/village.se.kts".toRL())

            event.player.sendMessage("[§6HollowStory] Сюжет успешно скомпилирован :)".toSTC(), event.player.uuid)
        } catch (e: Exception) {
            e.printStackTrace()
            event.player.sendMessage("[§6HollowStory] Ошибка при компиляции сюжета :(".toSTC(), event.player.uuid)
        }
    }

    @JvmStatic
    fun onPlayerClone(event: PlayerEvent.Clone) {

        StoryStorage.teams.forEach { team ->
            team.players.forEach { player ->
                if (player.uuid == event.player.uuid) {
                    //Заменяем старого игрока на нового, это срабатывает при смерти игрока или при входе в другой мир
                    player.mcPlayer = event.player
                }
            }
        }
    }
}