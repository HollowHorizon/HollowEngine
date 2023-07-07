package ru.hollowhorizon.hollowengine.common.scripting.story

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.nbt.CompoundNBT
import net.minecraft.util.ResourceLocation
import ru.hollowhorizon.hc.client.utils.nbt.ForCompoundNBT
import ru.hollowhorizon.hollowengine.common.exceptions.StoryEventException
import ru.hollowhorizon.hollowengine.common.exceptions.StoryPlayerNotFoundException
import ru.hollowhorizon.hollowengine.common.scripting.story.StoryExecutorThread
import ru.hollowhorizon.hollowengine.common.npcs.IHollowNPC
import java.util.*
import kotlin.collections.HashMap

@Serializable
class StoryTeam {
    val players = HashSet<StoryPlayer>()
    var completedEvents = HashSet<String>()

    @Transient //Transient because after restart we don't need to save story events, they will be started again
    var currentEvents = HashMap<String, StoryExecutorThread>()
    val eventsData = HashSet<StoryEventData>()
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
        return getAllOnline().minByOrNull { it.mcPlayer!!.distanceTo(npc.npcEntity) }
            ?: throw StoryEventException("No players in team")
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

@Serializable
class StoryEventData(
    val eventPath: String,
    val variables: @Serializable(ForCompoundNBT::class) CompoundNBT = CompoundNBT(), //Данное свойство отвечает за переменные созданные через выражение "by StoryStorage(...)"
    val stagedTasksStates: HashMap<Int, Int> = HashMap(), //Это свойство хранит индекс текущего блока кода
    val delayedTaskStates: HashMap<Int, Timer> = HashMap(), //Это свойство хранит прошедшее время задачи на ожидание
)

@Serializable
class Timer(var time: Float) {
    fun decrease(): Timer {
        time--
        return this
    }

    operator fun compareTo(value: Int): Int {
        return time.compareTo(value)
    }
}