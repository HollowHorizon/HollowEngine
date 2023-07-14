package ru.hollowhorizon.hollowengine.common.scripting.story

import com.google.common.reflect.TypeToken
import kotlinx.serialization.Serializable
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.entity.player.ServerPlayerEntity
import net.minecraft.nbt.EndNBT
import net.minecraft.network.play.server.SPlaySoundPacket
import net.minecraft.util.ResourceLocation
import net.minecraft.util.SoundCategory
import net.minecraft.util.math.AxisAlignedBB
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.vector.Vector3d
import net.minecraft.world.World
import net.minecraft.world.server.ServerWorld
import net.minecraftforge.fml.server.ServerLifecycleHooks
import ru.hollowhorizon.hc.client.utils.WorldHelper
import ru.hollowhorizon.hc.client.utils.nbt.NBTFormat
import ru.hollowhorizon.hc.client.utils.nbt.deserializeNoInline
import ru.hollowhorizon.hc.client.utils.nbt.serializeNoInline
import ru.hollowhorizon.hc.client.utils.toRL
import ru.hollowhorizon.hc.client.utils.toSTC
import ru.hollowhorizon.hc.common.capabilities.HollowCapabilityV2.Companion.get
import ru.hollowhorizon.hc.common.capabilities.syncEntity
import ru.hollowhorizon.hollowengine.common.capabilities.NPCEntityCapability
import ru.hollowhorizon.hollowengine.common.entities.NPCEntity
import ru.hollowhorizon.hollowengine.common.exceptions.StoryVariableNotFoundException
import ru.hollowhorizon.hollowengine.common.npcs.IHollowNPC
import ru.hollowhorizon.hollowengine.common.npcs.NPCSettings
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import kotlin.reflect.KProperty


open class StoryEvent(val team: StoryTeam, val eventPath: String) : IForgeEventScriptSupport {
    private val data = team.eventsData
        .find { it.eventPath == eventPath } ?: StoryEventData(eventPath)
        .also { team.eventsData.add(it) }
    private val eventNpcs: MutableList<IHollowNPC> = arrayListOf()
    private val atomicInteger = AtomicInteger()
    private val executor = Executors.newSingleThreadExecutor()
    private val delayedTasks = HashSet<DelayedTask>()
    override val forgeEvents = HashSet<ForgeEvent<*>>()
    var name: String = "Event <${this.eventPath}>"
    var description: String = "No description"
    var hideInEventList = true
    var safeForExit = false
    val progressManager = team.progressManager
    val world = StoryWorld(
        if (team.getHost().isOnline()) team.getHost().world as ServerWorld else team.getAllOnline()
            .first().mcPlayer!!.level as ServerWorld
    )
    val lock = Object()

    fun lock() = synchronized(lock) { lock.wait() }
    fun unlock() = synchronized(lock) { lock.notifyAll() }

    infix fun IHollowNPC.say(text: String): StoryEvent {
        team.getAllOnline() //для всех игроков команды, которые в сети
            .filter {
                it.distToSqr(
                    this.npcEntity.x,
                    this.npcEntity.y,
                    this.npcEntity.z
                ) < 2500
            } //Если игрок в радиусе 50 блоков от NPC
            .forEach { it.send("§6[§7${this.characterName}§6]§7 $text") } //Вывод сообщения от лица NPC
        return this@StoryEvent
    }

    fun <T> async(task: () -> T) = executor.submit(task) //Создать асинхронную задачу

    fun randomPos(distance: Int = 25, canPlayerSee: Boolean = false): BlockPos {
        val player = team.getHost().mcPlayer ?: team.getAllOnline().first().mcPlayer
        ?: throw IllegalStateException("No players in team online")

        var pos: BlockPos
        do {
            pos = WorldHelper.getHighestBlock(
                world.level,
                player.blockPosition().x + ((Math.random() * distance) - distance / 2).toInt(),
                player.blockPosition().z + ((Math.random() * distance) - distance / 2).toInt()
            )
            if (abs(pos.y - player.y) > 10) continue // Если игрок слишком далеко от точки, то ищем другую
        } while (player.canSee(pos) || canPlayerSee)

        return pos
    }

    private fun PlayerEntity.canSee(pos: BlockPos): Boolean {
        val from: Vector3d = this.getEyePosition(1f)
        val to = from.add(this.lookAngle.scale(128.0))

        return AxisAlignedBB(from, to).contains(pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble())
    }

    fun play(sound: String) {
        team.getAllOnline() //для всех игроков команды, которые в сети
            .forEach {
                (it.mcPlayer as ServerPlayerEntity).connection.send(
                    SPlaySoundPacket(
                        ResourceLocation(sound),
                        SoundCategory.MASTER,
                        it.mcPlayer!!.position(),
                        1.0f,
                        1.0f
                    )
                )
            }

    }


    fun wait(predicate: () -> Boolean) {
        while (predicate()) {
            Thread.sleep(100)
        }
    }

    fun makeNPC(settings: NPCSettings, level: World = this.world.level, pos: BlockPos): IHollowNPC {
        val npc = NPCEntity(level)
        npc.setPos(pos.x.toDouble() + 0.5, pos.y.toDouble(), pos.z.toDouble() + 0.5)
        level.addFreshEntity(npc)
        this.eventNpcs.add(npc)

        npc.getCapability(get<NPCEntityCapability>()).ifPresent { capability ->
            capability.settings = settings
            capability.syncEntity(npc)
        }
        npc.customName = settings.name.toSTC()
        npc.isCustomNameVisible = true

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
        this.team.eventsData.removeIf { this.eventPath == it.eventPath }
    }

    @Suppress("UnstableApiUsage")
    inner class StoryStorage<T : Any?>(var default: T) {
        private val typeToken = TypeToken.of(default!!.javaClass)

        @Suppress("UNCHECKED_CAST")
        operator fun getValue(current: Any?, property: KProperty<*>): T {
            val nbt = data.variables.get(property.name)

            if (nbt == null || nbt is EndNBT) {
                return default
            }

            return NBTFormat.deserializeNoInline(nbt, typeToken.rawType) as T
        }

        operator fun setValue(current: Any?, property: KProperty<*>, any: T) {
            default = any

            if (default == null) {
                data.variables.put(property.name, EndNBT.INSTANCE)
                return
            }
            data.variables.put(property.name, NBTFormat.serializeNoInline(default!!, typeToken.rawType))
        }
    }

    inner class StagedTask(vararg subTasks: () -> Unit) {
        private val thread = Thread {
            while (subTaskId < subTasks.size) {
                subTasks[subTaskId++]()
                data.stagedTasksStates[taskId] = subTaskId
            }
        }

        private val taskId = atomicInteger.getAndIncrement()
        private var subTaskId = data.stagedTasksStates.computeIfAbsent(taskId) { 0 }

        val complete: Boolean
            get() = thread.isAlive

        init {
            thread.start()
        }

        fun await() {
            while (thread.isAlive) {
                Thread.sleep(1000)
            }
        }
    }

    inner class DelayedTask(time: Float, task: () -> Unit) {
        private val taskId = atomicInteger.getAndIncrement()
        private val timer = data.delayedTaskStates.computeIfAbsent(taskId) { Timer(time) }

        val complete: Boolean
            get() = thread.isAlive

        private val thread = Thread {
            while (timer.decrease() > 0) {
                Thread.sleep(1000)
            }

            task()

            delayedTasks.remove(this)
        }

        init {
            delayedTasks.add(this)
            thread.start()
        }

        fun await() {
            while (thread.isAlive) {
                Thread.sleep(1000)
            }
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