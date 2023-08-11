package ru.hollowhorizon.hollowengine.common.scripting.story

import com.google.common.reflect.TypeToken
import com.mojang.math.Vector3d
import kotlinx.serialization.Serializable
import net.minecraft.core.BlockPos
import net.minecraft.nbt.EndTag
import net.minecraft.network.protocol.game.ClientboundCustomSoundPacket
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import net.minecraftforge.server.ServerLifecycleHooks
import ru.hollowhorizon.hc.client.utils.nbt.NBTFormat
import ru.hollowhorizon.hc.client.utils.nbt.deserializeNoInline
import ru.hollowhorizon.hc.client.utils.nbt.serializeNoInline
import ru.hollowhorizon.hc.client.utils.toRL
import ru.hollowhorizon.hc.client.utils.toSTC
import ru.hollowhorizon.hc.common.capabilities.HollowCapabilityV2.Companion.get
import ru.hollowhorizon.hc.common.capabilities.getCapability
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
        if (team.getHost().isOnline()) team.getHost().world as ServerLevel else team.getAllOnline()
            .first().mcPlayer!!.level as ServerLevel
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
            pos = world.level.getHeightmapPos(
                Heightmap.Types.WORLD_SURFACE_WG,
                BlockPos(
                    player.blockPosition().x + ((Math.random() * distance) - distance / 2).toInt(),
                    -666,
                    player.blockPosition().z + ((Math.random() * distance) - distance / 2).toInt()
                )
            )
            if (abs(pos.y - player.y) > 10) continue // Если игрок слишком далеко от точки, то ищем другую
        } while (player.canSee(pos) || canPlayerSee)

        return pos
    }

    private fun Player.canSee(pos: BlockPos): Boolean {
        val from: Vec3 = this.getEyePosition(1f)
        val to = from.add(this.lookAngle.scale(128.0))

        return AABB(from, to).contains(pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble())
    }

    fun play(sound: String) {
        team.getAllOnline() //для всех игроков команды, которые в сети
            .forEach {
                (it.mcPlayer as ServerPlayer).connection.send(
                    ClientboundCustomSoundPacket(
                        ResourceLocation(sound),
                        SoundSource.MASTER,
                        it.mcPlayer!!.position(),
                        1.0f,
                        1.0f,
                        it.mcPlayer!!.random.nextLong()
                    )
                )
            }

    }


    fun wait(predicate: () -> Boolean) {
        while (predicate()) {
            Thread.sleep(100)
        }
    }

    fun makeNPC(settings: NPCSettings, level: Level = this.world.level, pos: BlockPos): IHollowNPC {
        val npc = NPCEntity(level)
        npc.setPos(pos.x.toDouble() + 0.5, pos.y.toDouble(), pos.z.toDouble() + 0.5)
        level.addFreshEntity(npc)
        this.eventNpcs.add(npc)

        npc.getCapability(NPCEntityCapability::class).also { capability ->
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
        npc.npcEntity.remove(Entity.RemovalReason.DISCARDED)
    }

    fun wait(time: Float) {
        Thread.sleep((time * 1000).toLong())
    }

    fun clearEvent() {
        this.eventNpcs.forEach { it.npcEntity.remove(Entity.RemovalReason.DISCARDED) }
        this.progressManager.clear()
        this.team.eventsData.removeIf { this.eventPath == it.eventPath }
    }

    @Suppress("UnstableApiUsage")
    inner class StoryStorage<T : Any?>(var default: T) {
        private val typeToken = TypeToken.of(default!!.javaClass)

        @Suppress("UNCHECKED_CAST")
        operator fun getValue(current: Any?, property: KProperty<*>): T {
            val nbt = data.variables.get(property.name)

            if (nbt == null || nbt is EndTag) {
                return default
            }

            return NBTFormat.deserializeNoInline(nbt, typeToken.rawType) as T
        }

        operator fun setValue(current: Any?, property: KProperty<*>, any: T) {
            default = any

            if (default == null) {
                data.variables.put(property.name, EndTag.INSTANCE)
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