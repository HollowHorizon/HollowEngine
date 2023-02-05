package ru.hollowhorizon.hollowstory.story

import kotlinx.serialization.Serializable
import net.minecraft.client.Minecraft
import net.minecraft.client.audio.SimpleSound
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import net.minecraftforge.fml.loading.FMLEnvironment
import net.minecraftforge.fml.server.ServerLifecycleHooks
import net.minecraftforge.registries.ForgeRegistries
import ru.hollowhorizon.hc.HollowCore
import ru.hollowhorizon.hc.client.utils.toRL
import ru.hollowhorizon.hollowstory.common.entities.NPCEntity
import ru.hollowhorizon.hollowstory.common.exceptions.StoryVariableNotFoundException
import ru.hollowhorizon.hollowstory.common.exceptions.StoryVariableWrongTypeException
import ru.hollowhorizon.hollowstory.common.npcs.IHollowNPC
import ru.hollowhorizon.hollowstory.common.npcs.NPCSettings

open class StoryEvent(val team: StoryTeam, val variables: StoryVariables, val eventName: String) {
    private val eventNpcs: MutableList<IHollowNPC> = arrayListOf()
    var name: String = "Event <${this.eventName}>"
    var description: String = "No description"
    var hideInEventList = true
    var safeForExit = false
    val progressManager = StoryProgressManager()
    val level = team.getAllOnline().first().mcPlayer!!.level

    fun play(sound: String) {
        HollowCore.LOGGER.info("Playing sound ${ForgeRegistries.SOUND_EVENTS.getValue(sound.toRL())}")
        Minecraft.getInstance().soundManager.play(SimpleSound.forUI(ForgeRegistries.SOUND_EVENTS.getValue(sound.toRL()), 1F, 1F))
    }

    fun whenOnClient(task: () -> Unit) {
        if (FMLEnvironment.dist.isClient) {
            task()
        }
    }

    fun whenOnServer(task: () -> Unit) {
        if (!FMLEnvironment.dist.isClient) {
            task()
        }
    }

    fun createNPC(fromName: NPCSettings, level: World, pos: BlockPos): IHollowNPC {
        val npc = NPCEntity(fromName, level)
        npc.setPos(pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble())
        level.addFreshEntity(npc)
        this.eventNpcs.add(npc)
        return npc
    }

    fun createNPC(fromName: NPCSettings, level: String, pos: BlockPos): IHollowNPC {
        println("Level key: $level")
        val levelKeys = ServerLifecycleHooks.getCurrentServer().levelKeys()
        val levelKey = levelKeys.find { it.location().equals(level.toRL()) }

        val world = ServerLifecycleHooks.getCurrentServer()
            .getLevel(levelKey ?: throw StoryVariableNotFoundException("Dimension $level not found. Or not loaded"))!!
        println(world)
        return createNPC(fromName, world, pos)
    }

    fun removeNPC(npc: IHollowNPC) {
        this.eventNpcs.remove(npc)
        npc.npcEntity.remove()
    }

    fun wait(time: Float) {
        Thread.sleep((time * 1000).toLong())
    }

    fun clearEvent() {
        this.eventNpcs.forEach { it.npcEntity.remove() }
        this.progressManager.clearTasks()
    }
}

@Serializable
class StoryVariables {
    private val variables = ArrayList<StoryVariable>()

    @Suppress("UNCHECKED_CAST")
    fun <T> get(name: String): T {
        val variable = variables.find { it.name == name }
        if (variable != null) {
            return variable.value as T ?: throw StoryVariableWrongTypeException(name)
        } else {
            throw StoryVariableNotFoundException(name)
        }
    }

    fun <T> set(name: String, value: T) {
        val res = variables.find { it.name == name }

        if (res == null) {
            variables.add(StoryVariable(name, value))
            return
        } else {
            res.value = value
        }
    }
}

class StoryProgressManager {
    private val tasks = ArrayList<String>()

    fun addTask(task: String) {
        tasks.add(task)
    }

    fun removeTask(task: String) {
        tasks.remove(task)
    }

    fun removeLast() {
        tasks.removeAt(tasks.size - 1)
    }

    fun hasTask(task: String): Boolean {
        return tasks.contains(task)
    }

    fun clearTasks() {
        tasks.clear()
    }
}