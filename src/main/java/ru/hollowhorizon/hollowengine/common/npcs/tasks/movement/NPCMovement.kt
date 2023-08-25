package ru.hollowhorizon.hollowengine.common.npcs.tasks.movement

import net.minecraft.core.Direction
import net.minecraft.world.entity.Entity
import ru.hollowhorizon.hollowengine.common.npcs.IHollowNPC
import ru.hollowhorizon.hollowengine.common.npcs.tasks.HollowNPCTask
import ru.hollowhorizon.hollowengine.common.scripting.story.StoryTeam

class NPCMovement(val task: HollowNPCTask) {
    val keyframes = arrayListOf<MovementKeyframe>()
    val waiter = Object()
    var isActive = false

    fun front(distance: Int, moveConfig: MoveConfig.() -> Unit = {}): NPCMovement {
        if (isActive) return this

        val pos = task.npc.npcEntity.blockPosition()

        return when (task.npc.npcEntity.direction) {
            Direction.NORTH -> go(pos.x + distance, pos.y, pos.z, moveConfig)
            Direction.SOUTH -> go(pos.x - distance, pos.y, pos.z, moveConfig)
            Direction.EAST -> go(pos.x, pos.y, pos.z + distance, moveConfig)
            Direction.WEST -> go(pos.x, pos.y, pos.z - distance, moveConfig)
            else -> throw IllegalStateException("Can't go front when npc looking up/down?")
        }
    }

    fun back(distance: Int, moveConfig: MoveConfig.() -> Unit = {}): NPCMovement {
        if (isActive) return this

        val pos = task.npc.npcEntity.blockPosition()

        val keyframe = when (task.npc.npcEntity.direction) {
            Direction.EAST -> BackMovementKeyframe(task, MoveConfig().apply { moveConfig() }, pos.x + distance, pos.y, pos.z)
            Direction.WEST -> BackMovementKeyframe(task, MoveConfig().apply { moveConfig() }, pos.x - distance, pos.y, pos.z)
            Direction.NORTH -> BackMovementKeyframe(task, MoveConfig().apply { moveConfig() }, pos.x, pos.y, pos.z + distance)
            Direction.SOUTH -> BackMovementKeyframe(task, MoveConfig().apply { moveConfig() }, pos.x, pos.y, pos.z - distance)
            else -> throw IllegalStateException("Can't go front when npc looking up/down?")
        }
        keyframes.add(keyframe)

        return this
    }

    fun go(blockX: Int, blockY: Int, blockZ: Int, moveConfig: MoveConfig.() -> Unit = {}): NPCMovement {
        if (isActive) return this

        keyframes.add(BlockPosKeyframe(task, MoveConfig().apply { moveConfig() }, blockX, blockY, blockZ))
        return this
    }

    fun go(blockX: Int, blockZ: Int, moveConfig: MoveConfig.() -> Unit = {}): NPCMovement {
        if (isActive) return this

        keyframes.add(
            BlockPosKeyframe(
                task,
                MoveConfig().apply { moveConfig() },
                blockX,
                task.npc.npcEntity.blockPosition().y,
                blockZ
            )
        )
        return this
    }

    fun go(entity: Entity, moveConfig: MoveConfig.() -> Unit = {}): NPCMovement {
        if (isActive) return this

        keyframes.add(EntityKeyframe(task, MoveConfig().apply { moveConfig() }, entity))
        return this
    }

    fun go(entity: IHollowNPC, moveConfig: MoveConfig.() -> Unit = {}): NPCMovement {
        if (isActive) return this

        keyframes.add(EntityKeyframe(task, MoveConfig().apply { moveConfig() }, entity.npcEntity))
        return this
    }

    fun go(team: StoryTeam, moveConfig: MoveConfig.() -> Unit = {}): NPCMovement {
        if (isActive) return this

        keyframes.add(TeamKeyframe(task, MoveConfig().apply { moveConfig() }, team))
        return this
    }

    fun avoid(x: Int, y: Int, z: Int, moveConfig: MoveConfig.() -> Unit = {}): NPCMovement {
        if (isActive) return this

        keyframes.add(AvoidBlockPosKeyframe(task, MoveConfig().apply { moveConfig() }, x, y, z))
        return this
    }

    fun avoid(entity: Entity, moveConfig: MoveConfig.() -> Unit = {}): NPCMovement {
        if (isActive) return this

        keyframes.add(AvoidEntityKeyframe(task, MoveConfig().apply { moveConfig() }, entity))
        return this
    }

    fun avoid(entity: IHollowNPC, moveConfig: MoveConfig.() -> Unit = {}): NPCMovement {
        if (isActive) return this

        keyframes.add(AvoidEntityKeyframe(task, MoveConfig().apply { moveConfig() }, entity.npcEntity))
        return this
    }

    fun avoid(team: StoryTeam, moveConfig: MoveConfig.() -> Unit = {}): NPCMovement {
        if (isActive) return this

        keyframes.add(AvoidTeamKeyframe(task, MoveConfig().apply { moveConfig() }, team))
        return this
    }

    class MoveConfig {
        var speed: Double = 1.0
        var timeOut: Float = -1f
        var endDistance: Double = 1.0
            set(value) {
                field = value * value //сразу берём квадрат, чтобы не вычислять каждый раз корень
            }

        var onTimeout: (HollowNPCTask) -> Unit = {}
        var onStuck: (HollowNPCTask) -> Unit = {}
        var onTick: (HollowNPCTask) -> Unit = {}

    }

    fun async() {
        isActive = true
        task.isActive = true
    }

    fun await() {
        isActive = true
        task.isActive = true

        synchronized(waiter) {
            waiter.wait()
        }
    }
}