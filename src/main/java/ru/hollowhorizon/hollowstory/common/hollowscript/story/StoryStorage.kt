package ru.hollowhorizon.hollowstory.common.hollowscript.story

import net.minecraft.entity.player.PlayerEntity
import ru.hollowhorizon.hollowstory.common.exceptions.StoryEventException
import ru.hollowhorizon.hollowstory.story.StoryTeam

object StoryStorage {
    val teams = ArrayList<StoryTeam>()

    fun getTeam(player: PlayerEntity): StoryTeam {
        return teams.find { it.isFromTeam(player) }
            ?: throw StoryEventException("Player ${player.name.string} is not in any team")
    }
}