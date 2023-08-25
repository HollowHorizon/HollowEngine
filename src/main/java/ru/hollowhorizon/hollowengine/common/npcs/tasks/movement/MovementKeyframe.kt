package ru.hollowhorizon.hollowengine.common.npcs.tasks.movement

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.tags.BlockTags
import net.minecraft.util.Mth
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.ai.util.DefaultRandomPos
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.pathfinder.Path
import net.minecraft.world.phys.Vec3
import ru.hollowhorizon.hollowengine.common.npcs.tasks.HollowNPCTask
import ru.hollowhorizon.hollowengine.common.scripting.story.StoryTeam

interface MovementKeyframe {
    val config: NPCMovement.MoveConfig
    val task: HollowNPCTask

    fun tick()
    fun isFinished(): Boolean
}

class BackMovementKeyframe(
    override val task: HollowNPCTask,
    override val config: NPCMovement.MoveConfig,
    x: Int, y: Int, z: Int
) : MovementKeyframe {
    val mob = this.task.npc.npcEntity
    private val navigator = task.npc.npcEntity.navigation
    val x = x.toDouble() + 0.5
    val y = y.toDouble()
    val z = z.toDouble() + 0.5
    var timer = 0f

    override fun tick() {
        val d0: Double = this.x - mob.x
        val d1: Double = this.z - mob.z
        val d2: Double = this.y - mob.y
        val d3 = d0 * d0 + d2 * d2 + d1 * d1
        if (d3 < 2.5000003E-7) {
            mob.setZza(0.0f)
            return
        }

        val f9 = (Mth.atan2(d1, d0) * (180f / Math.PI.toFloat()).toDouble()).toFloat() + 90.0f
        mob.yRot = this.rotlerp(this.mob.yRot, f9, 90.0f)
        mob.speed = -(config.speed * this.mob.getAttributeValue(Attributes.MOVEMENT_SPEED)).toFloat()
        mob.setDeltaMovement(d0 * mob.speed, 0.0, d1 * mob.speed)
        val blockpos: BlockPos = this.mob.blockPosition()
        val blockstate: BlockState = this.mob.level.getBlockState(blockpos)
        val voxelshape = blockstate.getCollisionShape(this.mob.level, blockpos)
        if (
            d2 > this.mob.stepHeight.toDouble() &&
            d0 * d0 + d1 * d1 < 1.0f.coerceAtLeast(this.mob.bbWidth).toDouble() ||
            !voxelshape.isEmpty && this.mob.y < voxelshape.max(Direction.Axis.Y) + blockpos.y.toDouble() &&
            !blockstate.`is`(BlockTags.DOORS) && !blockstate.`is`(BlockTags.FENCES)
        ) {
            mob.jumpControl.jump()
        }

        if (navigator.isStuck) config.onStuck(task)
        if (timer >= config.timeOut && config.timeOut > 0f) config.onTimeout(task)

        config.onTick(task)

        timer += 0.05f //0.05f * 20 = 1 second
    }

    protected fun rotlerp(pSourceAngle: Float, pTargetAngle: Float, pMaximumChange: Float): Float {
        var f = Mth.wrapDegrees(pTargetAngle - pSourceAngle)
        if (f > pMaximumChange) {
            f = pMaximumChange
        }
        if (f < -pMaximumChange) {
            f = -pMaximumChange
        }
        var f1 = pSourceAngle + f
        if (f1 < 0.0f) {
            f1 += 360.0f
        } else if (f1 > 360.0f) {
            f1 -= 360.0f
        }
        return f1
    }

    override fun isFinished(): Boolean {
        return (navigator.path != null && navigator.isDone && task.npc.npcEntity.isOnGround) || (timer >= config.timeOut && config.timeOut > 0f) || task.npc.npcEntity.distanceToSqr(
            x,
            y,
            z
        ) <= config.endDistance
    }

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
            val pos = DefaultRandomPos.getPosAway(task.npc.npcEntity, 16, 7, Vec3(x, y, z))
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
            val pos = DefaultRandomPos.getPosAway(task.npc.npcEntity, 16, 7, entity.position())
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
            val pos = DefaultRandomPos.getPosAway(
                task.npc.npcEntity, 16, 7, team.nearestTo(task.npc).mcPlayer!!.position()
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