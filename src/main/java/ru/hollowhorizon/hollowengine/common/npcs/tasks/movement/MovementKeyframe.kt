package ru.hollowhorizon.hollowengine.common.npcs.tasks.movement

import net.minecraft.entity.Entity
import net.minecraft.entity.ai.RandomPositionGenerator
import net.minecraft.pathfinding.Path
import net.minecraft.util.math.vector.Vector3d
import ru.hollowhorizon.hollowengine.common.npcs.tasks.HollowNPCTask
import ru.hollowhorizon.hollowengine.common.scripting.story.StoryTeam

interface MovementKeyframe {
    val config: NPCMovement.MoveConfig
    val task: HollowNPCTask

    fun tick()
    fun isFinished(): Boolean
}

class BlockPosKeyframe(
    override val task: HollowNPCTask,
    override val config: NPCMovement.MoveConfig,
    x: Int, y: Int, z: Int,
) : MovementKeyframe {
    private val navigator = task.npc.npcEntity.navigation
    private var path: Path? = null
    val x = x.toDouble() + 0.5
    val y = y.toDouble()
    val z = z.toDouble() + 0.5

    var timer = 0f

    override fun tick() {
        if (path == null) path = navigator.createPath(x, y, z, 0)

        navigator.moveTo(path, config.speed)

        if (navigator.isStuck) config.onStuck(task)
        if (timer >= config.timeOut && config.timeOut > 0f) config.onTimeout(task)

        config.onTick(task)

        timer += 0.05f //0.05f * 20 = 1 second
    }

    override fun isFinished(): Boolean { //Проверка на (path != 0 и isDone) необходима, чтобы во время падения/телепортации не считалось, что нпс уже добрался до точки, т.к. в этот момент путь - null
        return (navigator.path != null && navigator.isDone && task.npc.npcEntity.isOnGround) || (timer >= config.timeOut && config.timeOut > 0f) || task.npc.npcEntity.distanceToSqr(
            x,
            y,
            z
        ) <= config.endDistance
    }
}

class EntityKeyframe(
    override val task: HollowNPCTask,
    override val config: NPCMovement.MoveConfig,
    val entity: Entity,
) : MovementKeyframe {
    private val navigator = task.npc.npcEntity.navigation
    private var path: Path? = null
    var timer = 0f

    override fun tick() {
        path = navigator.createPath(entity, 0) //Обновлять путь каждый тик нужно, т.к. сущность может двигаться
        navigator.moveTo(path, config.speed)

        if (navigator.isStuck) config.onStuck(task)
        if (timer >= config.timeOut && config.timeOut > 0f) config.onTimeout(task)

        config.onTick(task)

        timer += 0.05f //0.05f * 20 = 1 second
    }

    override fun isFinished(): Boolean {
        return (navigator.path != null && navigator.isDone && task.npc.npcEntity.isOnGround) || (timer >= config.timeOut && config.timeOut > 0f) || task.npc.npcEntity.distanceToSqr(
            entity
        ) <= config.endDistance
    }
}

class TeamKeyframe(
    override val task: HollowNPCTask,
    override val config: NPCMovement.MoveConfig,
    val team: StoryTeam,
) : MovementKeyframe {
    private val navigator = task.npc.npcEntity.navigation
    private var path: Path? = null
    var timer = 0f

    override fun tick() {
        path = navigator.createPath(
            team.nearestTo(task.npc).mcPlayer!!,
            0
        ) //Обновлять путь каждый тик нужно, т.к. нпс ищет ближайшего игрока, а они все время двигаются
        navigator.moveTo(path, config.speed)

        if (navigator.isStuck) config.onStuck(task)
        if (timer >= config.timeOut && config.timeOut > 0f) config.onTimeout(task)

        config.onTick(task)

        timer += 0.05f //0.05f * 20 = 1 second
    }

    override fun isFinished(): Boolean {
        return (navigator.path != null && navigator.isDone && task.npc.npcEntity.isOnGround) || (timer >= config.timeOut && config.timeOut > 0f) || task.npc.npcEntity.distanceToSqr(
            team.nearestTo(task.npc).mcPlayer!!
        ) <= config.endDistance
    }
}

class AvoidBlockPosKeyframe(
    override val task: HollowNPCTask,
    override val config: NPCMovement.MoveConfig,
    x: Int, y: Int, z: Int,
) : MovementKeyframe {
    private val navigator = task.npc.npcEntity.navigation
    private var path: Path? = null
    val x = x.toDouble() + 0.5
    val y = y.toDouble()
    val z = z.toDouble() + 0.5

    var timer = 0f

    override fun tick() {
        if (path == null) {
            val pos = RandomPositionGenerator.getPosAvoid(task.npc.npcEntity, 16, 7, Vector3d(x, y, z))
            path = if (pos != null) navigator.createPath(pos.x, pos.y, pos.z, 0)
            else navigator.createPath(x, y, z, 0)
        }
        navigator.moveTo(path, config.speed)

        if (navigator.isStuck) config.onStuck(task)
        if (timer >= config.timeOut && config.timeOut > 0f) config.onTimeout(task)

        config.onTick(task)

        timer += 0.05f //0.05f * 20 = 1 second
    }

    override fun isFinished(): Boolean {
        return (navigator.path != null && navigator.isDone && task.npc.npcEntity.isOnGround) || (timer >= config.timeOut && config.timeOut > 0f)
    }
}

class AvoidEntityKeyframe(
    override val task: HollowNPCTask,
    override val config: NPCMovement.MoveConfig,
    val entity: Entity,
) : MovementKeyframe {
    private val navigator = task.npc.npcEntity.navigation
    private var path: Path? = null
    var timer = 0f
    var lastRecalc = 0

    override fun tick() {
        if (path == null || lastRecalc++ >= 20) {
            lastRecalc = 0
            val pos = RandomPositionGenerator.getPosAvoid(task.npc.npcEntity, 16, 7, entity.position())
            if (pos != null) {
                path = navigator.createPath(pos.x, pos.y, pos.z, 0)
            }
        }
        navigator.moveTo(path, config.speed)

        if (navigator.isStuck) config.onStuck(task)
        if (timer >= config.timeOut && config.timeOut > 0f) config.onTimeout(task)

        config.onTick(task)

        timer += 0.05f //0.05f * 20 = 1 second
    }

    override fun isFinished(): Boolean {
        return (navigator.path != null && navigator.isDone && task.npc.npcEntity.isOnGround) || (timer >= config.timeOut && config.timeOut > 0f)
    }
}

class AvoidTeamKeyframe(
    override val task: HollowNPCTask,
    override val config: NPCMovement.MoveConfig,
    val team: StoryTeam,
) : MovementKeyframe {
    private val navigator = task.npc.npcEntity.navigation
    private var path: Path? = null
    var timer = 0f
    var lastRecalc = 0

    override fun tick() {
        if (path == null || lastRecalc++ >= 20) {
            lastRecalc = 0
            val pos = RandomPositionGenerator.getPosAvoid(
                task.npc.npcEntity,
                16,
                7,
                team.nearestTo(task.npc).mcPlayer!!.position()
            )
            if (pos != null) {
                path = navigator.createPath(pos.x, pos.y, pos.z, 0)
            }
        }
        navigator.moveTo(path, config.speed)

        if (navigator.isStuck) config.onStuck(task)
        if (timer >= config.timeOut && config.timeOut > 0f) config.onTimeout(task)

        config.onTick(task)

        timer += 0.05f //0.05f * 20 = 1 second
    }

    override fun isFinished(): Boolean {
        return (navigator.path != null && navigator.isDone && task.npc.npcEntity.isOnGround) || (timer >= config.timeOut && config.timeOut > 0f)
    }
}