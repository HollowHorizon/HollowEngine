package ru.hollowhorizon.hollowstory.story

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.util.ResourceLocation
import ru.hollowhorizon.hollowstory.common.exceptions.StoryEventException
import ru.hollowhorizon.hollowstory.common.exceptions.StoryPlayerNotFoundException
import ru.hollowhorizon.hollowstory.common.hollowscript.story.StoryExecutorThread
import ru.hollowhorizon.hollowstory.common.npcs.IHollowNPC
import java.util.*
import kotlin.collections.HashSet

@Serializable
class StoryTeam {
    /**
     * List of players in this team
     */
    val players = HashSet<StoryPlayer>()
    val variables = StoryVariables()
    /**
     * List of completed events from this team
     */
    var completedEvents = HashSet<String>()
    /**
     * List of current events from this team
     */
    @Transient //Transient because after restart we don't need to save story events, they will be started again
    var currentEvents = HashMap<String, StoryExecutorThread>()

    var progressManager = StoryProgressManager()

    fun add(player: PlayerEntity): StoryPlayer {
        val storyPlayer = StoryPlayer(player)
        players.add(storyPlayer)
        return storyPlayer
    }

    fun remove(player: PlayerEntity) {
        players.removeIf { it.uuid == player.uuid }
    }

    fun forAllOnline(action: (StoryPlayer) -> Unit) {
        players.filter { it.isOnline() }.forEach(action)
    }

    fun forAllOnlineExceptHost(action: (StoryPlayer) -> Unit) {
        players.filter { it.isOnline() && !it.isHost }.forEach(action)
    }

    fun getAllOnline(): List<StoryPlayer> {
        return players.filter { it.isOnline() }
    }

    fun getHost(): StoryPlayer {
        return players.find { it.isHost } ?: throw StoryEventException("Host not found")
    }

    fun getPlayer(uuid: UUID): StoryPlayer {
        val player = players.find { it.uuid == uuid }
        return player ?: throw StoryPlayerNotFoundException(uuid)
    }

    fun getPlayer(name: String): StoryPlayer {
        val player = players.find { it.name == name }
        return player ?: throw StoryPlayerNotFoundException(name)
    }

    fun getPlayer(player: PlayerEntity): StoryPlayer {
        val fplayer = players.find { it.uuid == player.uuid }
        return fplayer ?: throw StoryPlayerNotFoundException(player.uuid)
    }

    fun isFromTeam(uuid: UUID): Boolean {
        return players.find { it.uuid == uuid } != null
    }

    fun isFromTeam(name: String): Boolean {
        return players.find { it.mcPlayer?.name?.string == name } != null
    }

    fun isFromTeam(player: PlayerEntity): Boolean {
        return players.find { it.uuid == player.uuid } != null
    }

    fun hasPlayer(player: PlayerEntity): Boolean {
        return players.find { it.uuid == player.uuid } != null
    }

    fun nearestTo(npc: IHollowNPC): StoryPlayer {
        return getAllOnline().minByOrNull { it.mcPlayer!!.distanceTo(npc.npcEntity) } ?: throw StoryEventException("No players in team")
    }

    fun openDialogue(toRL: ResourceLocation) {

    }

    fun sendMessage(text: String) {
        forAllOnline { it.send(text) }
    }

    operator fun contains(player: PlayerEntity): Boolean {
        return isFromTeam(player)
    }

    fun updatePlayer(player: PlayerEntity) {
        this.players.find { it.uuid == player.uuid }?.mcPlayer = player
    }
}