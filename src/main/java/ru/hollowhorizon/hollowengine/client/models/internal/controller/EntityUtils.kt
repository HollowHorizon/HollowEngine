package ru.hollowhorizon.hollowengine.client.models.internal.controller

import net.minecraft.client.Minecraft
import net.minecraft.util.Mth
import net.minecraft.world.entity.LivingEntity
import kotlin.math.abs

private val LivingEntity.animationSpeed: Float
    get() = calculateSpeedViaDeltaMovement(this)
const val MOVEMENT_FACTOR = (1 / 256f)
val LivingEntity.isMoving get() = abs(animationSpeed) >= MOVEMENT_FACTOR

fun calculateSpeedViaDeltaMovement(entity: LivingEntity): Float {
    val vel = entity.deltaMovement
    val dx = vel.x.toFloat()
    val dz = vel.z.toFloat()

    val yawRad = Math.toRadians(entity.yBodyRot.toDouble()).toFloat()
    val forwardX = -Mth.sin(yawRad)
    val forwardZ = Mth.cos(yawRad)

    val dot = dx * forwardX + dz * forwardZ

    val deltaRot = entity.yBodyRot - entity.yBodyRotO

    return (dot * 20f + deltaRot / 10f) * if(Minecraft.getInstance().isPaused) 0f else 1f
}