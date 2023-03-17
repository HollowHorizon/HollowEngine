package ru.hollowhorizon.hollowstory.story

import kotlinx.serialization.Serializable
import net.minecraft.client.Minecraft
import net.minecraft.client.audio.SimpleSound
import net.minecraft.entity.EntityType
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.RayTraceContext
import net.minecraft.util.math.RayTraceResult
import net.minecraft.util.math.vector.Vector3d
import net.minecraft.world.World
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.fml.loading.FMLEnvironment
import net.minecraftforge.fml.server.ServerLifecycleHooks
import net.minecraftforge.registries.ForgeRegistries
import ru.hollowhorizon.hc.client.utils.WorldHelper
import ru.hollowhorizon.hc.client.utils.toRL
import ru.hollowhorizon.hc.common.capabilities.HollowCapabilityV2.Companion.get
import ru.hollowhorizon.hc.common.capabilities.syncEntity
import ru.hollowhorizon.hollowstory.common.capabilities.NPCEntityCapability
import ru.hollowhorizon.hollowstory.common.entities.NPCEntity
import ru.hollowhorizon.hollowstory.common.exceptions.StoryVariableNotFoundException
import ru.hollowhorizon.hollowstory.common.exceptions.StoryVariableWrongTypeException
import ru.hollowhorizon.hollowstory.common.npcs.IHollowNPC
import ru.hollowhorizon.hollowstory.common.npcs.NPCSettings
import ru.hollowhorizon.hollowstory.dialogues.generateEntityNBT
import ru.hollowhorizon.hollowstory.story.features.IDialogueFeature
import ru.hollowhorizon.hollowstory.story.features.IReplayFeature
import ru.hollowhorizon.hollowstory.story.features.ITransitionFeature
import kotlin.math.abs

open class StoryEvent(val team: StoryTeam, val variables: StoryVariables, val eventName: String) :
    ITransitionFeature, IDialogueFeature, IReplayFeature {
    val forgeEvents = HashSet<ForgeEvent<*>>()
    private val eventNpcs: MutableList<IHollowNPC> = arrayListOf()
    var name: String = "Event <${this.eventName}>"
    var description: String = "No description"
    var hideInEventList = true
    var safeForExit = false
    val progressManager = team.progressManager
    val level = team.getAllOnline().first().mcPlayer!!.level
    val lock = Object()

    fun lock() = synchronized(lock) { lock.wait() }
    fun unlock() = synchronized(lock) { lock.notifyAll() }

    fun randomPos(distance: Int = 25, canPlayerSee: Boolean = false): BlockPos {
        val player = team.getHost().mcPlayer ?: team.getAllOnline().first().mcPlayer
        ?: throw IllegalStateException("No players in team online")

        while (true) {
            val start = WorldHelper.getHighestBlock(
                level,
                player.blockPosition().x + ((Math.random() * distance) - distance / 2).toInt(),
                player.blockPosition().z + ((Math.random() * distance) - distance / 2).toInt()
            )
            if (abs(start.y - player.blockPosition().y) > 10) continue // Если игрок слишком далеко от точки, то ищем другую
            if (!player.canSee(start) || canPlayerSee) return start
        }
    }

    private fun PlayerEntity.canSee(pos: BlockPos): Boolean {
        return this.level.clip(
            RayTraceContext(
                this.position(),
                Vector3d(pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble()),
                RayTraceContext.BlockMode.COLLIDER,
                RayTraceContext.FluidMode.NONE,
                entity
            )
        ).type == RayTraceResult.Type.MISS
    }

    fun play(sound: String) {
        Minecraft.getInstance().soundManager.play(
            SimpleSound.forUI(
                ForgeRegistries.SOUND_EVENTS.getValue(sound.toRL()),
                1F,
                1F
            )
        )
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

    fun makeNPC(fromName: NPCSettings, level: World = this.level, pos: BlockPos): IHollowNPC {
        val npc = NPCEntity(level)
        npc.setPos(pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble())
        level.addFreshEntity(npc)
        this.eventNpcs.add(npc)

        npc.getCapability(get<NPCEntityCapability>()).ifPresent { cap: NPCEntityCapability ->
            cap.settings = fromName
            val entity = cap.settings.puppetEntity.split("@".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            val nbt = if (entity.size > 1) entity[1] else ""
            npc.puppet = EntityType.loadEntityRecursive(generateEntityNBT(entity[0], nbt), level) { e -> e }

            cap.syncEntity(npc)
        }

        return npc
    }

    fun makeNPC(fromName: NPCSettings, level: String, pos: BlockPos): IHollowNPC {
        println("Level key: $level")
        val levelKeys = ServerLifecycleHooks.getCurrentServer().levelKeys()
        val levelKey = levelKeys.find { it.location().equals(level.toRL()) }

        val world = ServerLifecycleHooks.getCurrentServer()
            .getLevel(levelKey ?: throw StoryVariableNotFoundException("Dimension $level not found. Or not loaded"))!!
        println(world)
        return makeNPC(fromName, world, pos)
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
        this.progressManager.clear()
        this.forgeEvents.forEach { MinecraftForge.EVENT_BUS.unregister(it) }
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

@Serializable
class StoryProgressManager {
    private val tasks = LinkedHashSet<String>()
    var shouldUpdate = false

    fun addTask(task: String) {
        tasks.add(task)
        shouldUpdate = true
    }

    fun removeTask(task: String) {
        tasks.remove(task)
        shouldUpdate = true
    }

    fun removeLast() {
        if (tasks.size > 0) tasks.remove(tasks.last())
        shouldUpdate = true
    }

    fun removeFirst() {
        if (tasks.size > 0) tasks.remove(tasks.first())
        shouldUpdate = true
    }

    fun hasTask(task: String): Boolean {
        return tasks.contains(task)
    }

    fun clear() {
        tasks.clear()
        shouldUpdate = true
    }

    fun tasks() = tasks
}