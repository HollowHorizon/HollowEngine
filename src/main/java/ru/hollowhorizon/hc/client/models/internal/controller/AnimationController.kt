package ru.hollowhorizon.hc.client.models.internal.controller

import net.minecraft.util.Mth
import net.minecraft.world.entity.LivingEntity
import ru.hollowhorizon.hc.client.render.entity.HollowEntityRenderer.Companion.MOVEMENT_FACTOR
import kotlin.math.abs

private val LivingEntity.animationSpeed: Float
    get() = calculateSpeedViaDeltaMovement(this)

private fun LivingEntity.isMoving() = abs(animationSpeed) >= MOVEMENT_FACTOR

fun calculateSpeedViaDeltaMovement(entity: LivingEntity): Float {
    // 1) берём горизонтальную часть вектора скорости (блоки/тик)
    val vel = entity.deltaMovement
    val dx = vel.x.toFloat()
    val dz = vel.z.toFloat()

    // 2) вектор «вперед» по ориентации тела
    val yawRad = Math.toRadians(entity.yBodyRot.toDouble()).toFloat()
    val forwardX = -Mth.sin(yawRad)
    val forwardZ = Mth.cos(yawRad)

    // 3) проекция вектора скорости на вектор «вперед» (чтобы знать направленную скорость)
    val dot = dx * forwardX + dz * forwardZ
    
    val deltaRot = entity.yBodyRot - entity.yBodyRotO
    
    // 4) переводим блоки/тик → блоки/сек
    return dot * 20f + deltaRot / 10f
}
