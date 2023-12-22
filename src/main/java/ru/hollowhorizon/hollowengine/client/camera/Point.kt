package ru.hollowhorizon.hollowengine.client.camera

import com.mojang.math.Vector3d
import com.mojang.math.Vector3f
import kotlinx.serialization.Serializable


@Serializable
class Point(
    val x: Double, val y: Double, val z: Double,
    val xRot: Float, val yRot: Float, val zRot: Float,
) {
    val pos get() = Vector3d(x, y, z)
    val rot get() = Vector3f(xRot, yRot, zRot)

    fun toLocal(startPos: Vector3d) = Point(x - startPos.x, y - startPos.y, z - startPos.z, xRot, yRot, zRot)
}