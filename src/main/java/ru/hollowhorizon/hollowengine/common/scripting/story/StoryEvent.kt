package ru.hollowhorizon.hollowengine.common.scripting.story

import com.google.common.reflect.TypeToken
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
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import net.minecraftforge.registries.ForgeRegistries
import net.minecraftforge.server.ServerLifecycleHooks
import ru.hollowhorizon.hc.client.gltf.animations.manager.AnimatedEntityCapability
import ru.hollowhorizon.hc.client.utils.mcText
import ru.hollowhorizon.hc.client.utils.nbt.NBTFormat
import ru.hollowhorizon.hc.client.utils.nbt.deserializeNoInline
import ru.hollowhorizon.hc.client.utils.nbt.serializeNoInline
import ru.hollowhorizon.hc.client.utils.rl
import ru.hollowhorizon.hc.common.capabilities.CapabilityStorage
import ru.hollowhorizon.hc.common.network.send
import ru.hollowhorizon.hollowengine.client.screen.DrawMousePacket
import ru.hollowhorizon.hollowengine.common.entities.NPCEntity
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager.fromReadablePath
import ru.hollowhorizon.hollowengine.common.network.*
import ru.hollowhorizon.hollowengine.common.npcs.IHollowNPC
import ru.hollowhorizon.hollowengine.common.npcs.NPCSettings
import ru.hollowhorizon.hollowengine.common.npcs.SpawnLocation
import ru.hollowhorizon.hollowengine.common.registry.ModEntities
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

    fun NPCEntity.Companion.getOrCreate(npc: NPCSettings, location: SpawnLocation): NPCEntity {
        val server = ServerLifecycleHooks.getCurrentServer()
        val dimension = server.levelKeys().find { it.location() == location.world.rl }
            ?: throw IllegalStateException("Dimension ${location.world} not found. Or not loaded!")
        val level = server.getLevel(dimension)
            ?: throw IllegalStateException("Dimension ${location.world} not found. Or not loaded")

        val entities = level.getEntities(ModEntities.NPC_ENTITY.get()) { entity ->
            return@getEntities entity.model == npc.model.rl && entity.characterName == npc.name && entity.isAlive
        }

        val entity = entities.firstOrNull() ?: NPCEntity(level, npc.model).apply {
            level.addFreshEntity(this)
        }
        entity.getCapability(CapabilityStorage.getCapability(AnimatedEntityCapability::class.java)).ifPresent {
            it.model = npc.model
        }
        entity.moveTo(
            location.pos.x.toDouble() + 0.5,
            location.pos.y.toDouble(),
            location.pos.z.toDouble() + 0.5,
            location.rotation.x,
            location.rotation.y
        )

        npc.data.attributes.forEach { (name, value) ->
            entity.getAttribute(ForgeRegistries.ATTRIBUTES.getValue(name.rl) ?: return@forEach)?.baseValue =
                value.toDouble()
        }

        entity.isCustomNameVisible = true
        entity.customName = npc.name.mcText

        bindNpc(entity)
        return entity
    }

    fun bindNpc(npc: IHollowNPC) = eventNpcs.add(npc)

    fun <T> async(task: () -> T) = executor.submit(task) //Создать асинхронную задачу

    fun randomPos(distance: Int = 25, canPlayerSee: Boolean = false): BlockPos {
        val player = team.getHost().mcPlayer ?: team.getAllOnline().first().mcPlayer
        ?: throw IllegalStateException("No players in team online")

        var attempt = 0
        var pos: BlockPos
        do {
            attempt++
            pos = world.level.getHeightmapPos(
                Heightmap.Types.WORLD_SURFACE_WG,
                BlockPos(
                    player.blockPosition().x + ((Math.random() * distance) - distance / 2).toInt(),
                    -666,
                    player.blockPosition().z + ((Math.random() * distance) - distance / 2).toInt()
                )
            )
            if (abs(pos.y - player.y) > 10) continue // Если игрок слишком далеко от точки, то ищем другую
        } while ((player.canSee(pos) || canPlayerSee) && attempt < 1000)

        return pos
    }

    fun NPCEntity.despawn() = removeNPC(this)

    fun chain(vararg task: () -> Unit) = StagedTask(*task)

    fun Player.canSee(to: BlockPos): Boolean {
        val from: Vec3 = this.getEyePosition(1f)

        return this.level.clip(
            ClipContext(
                from,
                Vec3(to.x.toDouble(), to.y.toDouble(), to.z.toDouble()),
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                this
            )
        ).type == HitResult.Type.MISS
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

    @JvmName("waitAction")
    fun wait() {
        DrawMousePacket().send(true, *team.getAllOnline().map { it.mcPlayer!! }.toTypedArray())
        MouseButtonWaitPacket().send(Container(MouseButton.RIGHT), *team.getAllOnline().map { it.mcPlayer!! }.toTypedArray())

        waitForgeEvent<ServerMouseClickedEvent> { event ->
            event.button == MouseButton.RIGHT && event.entity in team
        }

        MouseButtonWaitResetPacket().send("", *team.getAllOnline().map { it.mcPlayer!! }.toTypedArray())
        DrawMousePacket().send(false, *team.getAllOnline().map { it.mcPlayer!! }.toTypedArray())
    }

    fun removeNPC(npc: IHollowNPC) {
        this.eventNpcs.remove(npc)
        npc.npcEntity.remove(Entity.RemovalReason.DISCARDED)
    }

    fun wait(time: Float) {
        Thread.sleep((time * 1000).toLong())
    }

    fun wait(time: Int) = wait(time.toFloat())

    fun clearEvent() {
        this.eventNpcs.forEach { it.npcEntity.remove(Entity.RemovalReason.DISCARDED) }
        this.progressManager.clear()
        this.team.eventsData.removeIf { this.eventPath == it.eventPath }
    }

    val Int.minutes
        get() = this * 60

    val Int.hours
        get() = this * 60 * 60

    val Float.minutes
        get() = this * 60

    val Float.hours
        get() = this * 60 * 60

    @Suppress("UnstableApiUsage")
    inner class Local<T : Any?>(var initial: T, private val customName: String? = null) {
        private val typeToken = TypeToken.of(initial!!.javaClass)

        @Suppress("UNCHECKED_CAST")
        operator fun getValue(current: Any?, property: KProperty<*>): T {
            val nbt = data.variables.get(customName ?: property.name)

            if (nbt == null || nbt is EndTag) {
                return initial
            }

            return NBTFormat.deserializeNoInline(nbt, typeToken.rawType) as T
        }

        operator fun setValue(current: Any?, property: KProperty<*>, any: T) {
            initial = any

            if (initial == null) {
                data.variables.put(customName ?: property.name, EndTag.INSTANCE)
                return
            }
            data.variables.put(customName ?: property.name, NBTFormat.serializeNoInline(initial!!, typeToken.rawType))
        }
    }

    fun startScript(path: String) {
        StoryExecutorThread(team, path.fromReadablePath(), false).run()
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