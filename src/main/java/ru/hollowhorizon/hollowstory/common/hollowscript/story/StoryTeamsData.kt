package ru.hollowhorizon.hollowstory.common.hollowscript.story

import kotlinx.serialization.Serializable
import net.minecraft.nbt.CompoundNBT
import net.minecraft.world.World
import net.minecraft.world.storage.WorldSavedData
import ru.hollowhorizon.hc.common.capabilities.HollowCapabilityV2
import ru.hollowhorizon.hollowstory.story.StoryPlayer
import ru.hollowhorizon.hollowstory.story.StoryTeam

class StoryTeamsData : WorldSavedData("story_teams") {
    companion object {
        lateinit var INSTANCE: StoryTeamsData
    }

    override fun save(nbt: CompoundNBT): CompoundNBT {
        val teams = StoryStorage.teams
        val teamsNBT = CompoundNBT()

        for ((i, team) in teams.withIndex()) {
            val teamNBT = CompoundNBT()
            for ((j, player) in team.players.withIndex()) {
                val playerNBT = CompoundNBT()
                playerNBT.putUUID("uuid", player.uuid)
                playerNBT.putBoolean("isHost", player.isHost)
                teamNBT.put("$j", playerNBT)
            }
            teamsNBT.put("$i", teamNBT)
        }
        nbt.put("teams", teamsNBT)
        return nbt
    }

    override fun load(nbt: CompoundNBT) {
        if (!nbt.contains("teams")) return //Команд нету, ничего не делаем

        val teams = nbt.getCompound("teams")

        teams.allKeys.forEach { key ->
            val team = teams.getCompound(key)
            val storyTeam = StoryTeam()
            team.allKeys.forEach { key2 ->
                val player = team.getCompound(key2)
                val storyPlayer = StoryPlayer(player.getUUID("uuid"))
                storyPlayer.isHost = player.getBoolean("isHost")
                storyTeam.players.add(storyPlayer)
            }
            StoryStorage.teams.add(storyTeam)
        }
    }

    override fun isDirty() = true
}