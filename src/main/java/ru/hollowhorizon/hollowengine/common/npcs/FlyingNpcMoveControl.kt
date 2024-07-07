package ru.hollowhorizon.hollowengine.common.npcs

import net.minecraft.util.Mth
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.ai.control.MoveControl
import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.math.sqrt

class FlyingNpcMoveControl(pMob: Mob) : MoveControl(pMob) {
    private var speed = 0.1f

    override fun tick() {
        if (mob.horizontalCollision) {
            mob.yRot += 180.0f
            this.speed = 0.1f
        }

        var x: Double = wantedX - mob.getX()
        val y: Double = wantedY - mob.getY()
        var z: Double = wantedZ - mob.getZ()
        var length = sqrt(x * x + z * z)
        if (abs(length) > 9.999999747378752E-6) {
            val modifier = 1.0 - abs(y * 0.699999988079071) / length
            x *= modifier
            z *= modifier
            length = sqrt(x * x + z * z)
            val len = sqrt(x * x + z * z + y * y)
            val yRot: Float = mob.yRot
            val angleHorizontal = Mth.atan2(z, x).toFloat()
            val angleVertical: Float = Mth.wrapDegrees(mob.yRot + 90.0f)
            val result = Mth.wrapDegrees(angleHorizontal * 57.295776f)
            mob.yRot = Mth.approachDegrees(angleVertical, result, 4.0f) - 90.0f
            mob.yBodyRot = mob.yRot
            if (Mth.degreesDifferenceAbs(yRot, mob.yRot) < 3.0f) {
                this.speed = Mth.approach(this.speed, 1.8f, 0.005f * (1.8f / this.speed))
            } else {
                this.speed = Mth.approach(this.speed, 0.2f, 0.025f)
            }

            val yRotation = (-(Mth.atan2(-y, length) * 57.2957763671875)).toFloat()
            mob.xRot = yRotation
            val newYRot: Float = mob.yRot + 90.0f
            val nX = (this.speed * Mth.cos(newYRot * 0.017453292f)).toDouble() * abs(x / len)
            val nY = (this.speed * Mth.sin(newYRot * 0.017453292f)).toDouble() * abs(z / len)
            val nZ = (this.speed * Mth.sin(yRotation * 0.017453292f)).toDouble() * abs(y / len)
            val delta: Vec3 = mob.deltaMovement
            mob.deltaMovement = delta.add(Vec3(nX, nZ, nY).subtract(delta).scale(0.2))
        }
    }
}