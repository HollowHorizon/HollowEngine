package ru.hollowhorizon.hollowengine.client.models.internal.controller

import net.minecraft.client.Minecraft
import net.minecraft.util.Mth
import net.minecraft.world.entity.LivingEntity
import kotlin.math.abs
import kotlin.math.sign

private const val TURN_ANIMATION_DEAD_ZONE = 3f
private const val TURN_ANIMATION_FULL_ANGLE = 35f
private const val TURN_ANIMATION_SPEED = 0.8f

const val MOVEMENT_FACTOR = 1 / 256f

fun calculateSpeedViaDeltaMovement(entity: LivingEntity): Float {
    val vel = entity.deltaMovement
    val dx = vel.x.toFloat()
    val dz = vel.z.toFloat()

    val yawRad = Math.toRadians(entity.yBodyRot.toDouble()).toFloat()
    val forwardX = -Mth.sin(yawRad)
    val forwardZ = Mth.cos(yawRad)

    val forwardSpeed = dx * forwardX + dz * forwardZ
    val moveSpeed = forwardSpeed * 20f

    val bodyTurnDelta = Mth.wrapDegrees(entity.yBodyRot - entity.yBodyRotO)
    val absTurnDelta = abs(bodyTurnDelta)
    val turnSpeed = if (absTurnDelta <= TURN_ANIMATION_DEAD_ZONE) {
        0f
    } else {
        val normalizedTurn = ((absTurnDelta - TURN_ANIMATION_DEAD_ZONE) /
                (TURN_ANIMATION_FULL_ANGLE - TURN_ANIMATION_DEAD_ZONE)).coerceIn(0f, 1f)
        val easedTurn = normalizedTurn * normalizedTurn
        bodyTurnDelta.sign * easedTurn * TURN_ANIMATION_SPEED
    }

    val speed = moveSpeed + turnSpeed
    if (abs(speed) < MOVEMENT_FACTOR) return 0f

    return speed * if (Minecraft.getInstance().isPaused) 0f else 1f
}
