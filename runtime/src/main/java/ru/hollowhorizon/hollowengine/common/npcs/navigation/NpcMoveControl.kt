package ru.hollowhorizon.hollowengine.common.npcs.navigation

import net.minecraft.core.Direction
import net.minecraft.tags.BlockTags
import net.minecraft.util.Mth
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.ai.control.MoveControl
//? if > 1.20.1 {
/*import net.minecraft.world.level.pathfinder.PathType
import net.minecraft.world.level.pathfinder.PathfindingContext
*///?} else {
import net.minecraft.world.level.pathfinder.BlockPathTypes
//?}
import ru.hollowhorizon.hollowengine.common.entities.NpcEntity
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

class NpcMoveControl(mob: NpcEntity) : MoveControl(mob) {
    companion object {
        private const val MIN_DISTANCE_FOR_TURN_SQ = 0.04
        private const val BODY_TURN_DEAD_ZONE = 3f
        private const val MAX_BODY_TURN = 35f
    }

    override fun tick() {
        when (this.operation) {
            Operation.STRAFE -> {
                val speed = (this.speedModifier * mob.getAttributeValue(Attributes.MOVEMENT_SPEED)).toFloat()
                var forward = this.strafeForwards
                var right = this.strafeRight
                var norm = Mth.sqrt(forward * forward + right * right)
                if (norm < 1.0f) norm = 1.0f
                val scale = speed / norm
                forward *= scale
                right *= scale

                val yawRad = mob.yRot * Mth.DEG_TO_RAD
                val sin = Mth.sin(yawRad)
                val cos = Mth.cos(yawRad)

                val deltaX = forward * cos - right * sin
                val deltaZ = right * cos + forward * sin

                if (!this.isWalkable(deltaX, deltaZ)) {
                    this.strafeForwards = 1.0f
                    this.strafeRight = 0.0f
                }

                this.mob.speed = speed
                this.mob.zza = this.strafeForwards
                this.mob.xxa = this.strafeRight
                this.operation = Operation.WAIT
            }

            Operation.MOVE_TO -> {
                this.operation = Operation.WAIT
                val dx = this.wantedX - mob.x
                val dz = this.wantedZ - mob.z
                val dy = this.wantedY - mob.y
                val distSq = dx * dx + dy * dy + dz * dz
                val horizontalDistSq = dx * dx + dz * dz

                if (distSq < 2.5e-7) {
                    mob.zza = 0f
                    return
                }

                if (horizontalDistSq >= MIN_DISTANCE_FOR_TURN_SQ) {
                    val targetYaw = (Mth.atan2(dz, dx) * (180 / Math.PI) - 90.0).toFloat()
                    val yawDelta = Mth.wrapDegrees(targetYaw - mob.yBodyRot)
                    if (abs(yawDelta) >= BODY_TURN_DEAD_ZONE) {
                        val bodyYaw = rotlerp(mob.yBodyRot, targetYaw, MAX_BODY_TURN)
                        mob.yRot = bodyYaw
                        mob.setYBodyRot(bodyYaw)
                    }
                }
                mob.speed = (this.speedModifier * mob.getAttributeValue(Attributes.MOVEMENT_SPEED)).toFloat()

                val pos = mob.blockPosition()
                val state = mob.level().getBlockState(pos)
                val shape = state.getCollisionShape(mob.level(), pos)

                val needsJump = dy > mob.maxUpStep() &&
                        (dx * dx + dz * dz < max(sqrt(3.0), mob.bbWidth.toDouble())) // квадрат сравниваем с квадратом

                // Если высота отличается больше, чем maxUpStep, а коллизии нет — прыгаем
                if (needsJump || (!shape.isEmpty && mob.y < shape.max(Direction.Axis.Y) + pos.y
                            && !state.`is`(BlockTags.DOORS) && !state.`is`(BlockTags.FENCES))
                ) {
                    mob.jumpControl.jump()
                    this.operation = Operation.JUMPING
                }
            }

            Operation.JUMPING -> {
                mob.speed = (this.speedModifier * mob.getAttributeValue(Attributes.MOVEMENT_SPEED)).toFloat()
                if (mob.onGround()) {
                    this.operation = Operation.WAIT
                }
            }

            else -> {
                mob.zza = 0f
            }
        }
    }

    private fun isWalkable(relativeX: Float, relativeZ: Float): Boolean {
        val pathNavigation = mob.navigation
        val nodeEvaluator = pathNavigation.nodeEvaluator
        //? if > 1.20.1 {
        /*return nodeEvaluator.getPathType(
            PathfindingContext(mob.level(), mob), Mth.floor(
                mob.x + relativeX.toDouble()
            ), mob.blockY, Mth.floor(mob.z + relativeZ.toDouble())
        ) == PathType.WALKABLE
        *///?} else {
        return nodeEvaluator.getBlockPathType(
            mob.level(), Mth.floor(
                mob.x + relativeX.toDouble()
            ), mob.blockY, Mth.floor(mob.z + relativeZ.toDouble())
        ) == BlockPathTypes.WALKABLE
        //?}
    }
}
