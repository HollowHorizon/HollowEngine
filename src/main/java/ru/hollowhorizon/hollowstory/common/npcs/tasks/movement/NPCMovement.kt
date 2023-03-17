package ru.hollowhorizon.hollowstory.common.npcs.tasks.movement

import net.minecraft.entity.Entity
import ru.hollowhorizon.hollowstory.common.npcs.IHollowNPC
import ru.hollowhorizon.hollowstory.common.npcs.tasks.HollowNPCTask
import ru.hollowhorizon.hollowstory.story.StoryTeam

class NPCMovement(val task: HollowNPCTask) {
    val keyframes = arrayListOf<MovementKeyframe>()
    val waiter = Object()
    var isActive = false

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
                field =
                    value * value //Необходимо для сравнения с расстоянием до цели, чтобы каждый тик не вычислять квадратный корень, пустая трата ресурсов
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