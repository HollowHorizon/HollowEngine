package ru.hollowhorizon.hollowengine.common.story

import com.mojang.math.Vector3d
import net.minecraft.util.Mth
import net.minecraft.world.phys.Vec2
import ru.hollowhorizon.hc.client.math.Spline3D
import thedarkcolour.kotlinforforge.forge.vectorutil.minus
import thedarkcolour.kotlinforforge.forge.vectorutil.plus
import kotlin.math.acos
import kotlin.math.sin

open class CameraPlayer(
    vararg val pairs: Pair<Int, CameraNode>,
) {
    private var progress = 0
    private var nodeIndex = 0

    fun update(): Container {
        val (maxTime, node) = pairs[nodeIndex]
        progress++
        if (progress >= maxTime) {
            progress = 0
            nodeIndex++
        }
        if(nodeIndex >= pairs.size) {
            nodeIndex = 0
            onEnd()
        }
        return Container(
            node.updatePosition(progress / maxTime.toFloat()),
            node.updateRotation(progress / maxTime.toFloat())
        )
    }

    open fun onEnd() {

    }

    data class Container(val point: Vector3d, val rotation: Vec2)
}

interface CameraNode {
    fun updateRotation(progress: Float): Vec2
    fun updatePosition(progress: Float): Vector3d
}

class SplineNode(
    vararg points: Vector3d,
    private val beginRot: Vec2,
    private val endRot: Vec2,
    val interpolation: (Float) -> Float = { progress -> progress }
) : CameraNode {
    private val spline3D = Spline3D(points.toList())
    override fun updateRotation(progress: Float) = beginRot.lerp(endRot, interpolation(progress))
    override fun updatePosition(progress: Float) = spline3D.getPoint(interpolation(progress).toDouble())
}

class SimpleNode(
    private val begin: Vector3d,
    private val end: Vector3d,
    private val beginRot: Vec2,
    private val endRot: Vec2,
    val interpolation: (Float) -> Float = { progress -> progress }
) : CameraNode {
    override fun updateRotation(progress: Float) = beginRot.lerp(endRot, interpolation(progress))

    override fun updatePosition(progress: Float) = interpolation(progress) * (end - begin) + begin
}

private operator fun Float.times(vector3d: Vector3d): Vector3d {
    return Vector3d(this * vector3d.x, this * vector3d.y, this * vector3d.z)
}

private operator fun Float.times(vector3d: Vec2): Vec2 {
    return Vec2(this * vector3d.x, this * vector3d.y)
}

private fun Vec2.lerp(other: Vec2, alpha: Float): Vec2 {
    val beginX = this.x
    val beginY = this.y
    var endX = other.x
    var endY = other.y

    var dot = beginX * endX + beginY * endY
    if(dot < 0) {
        endX = -endX
        endY = -endY
        dot = -dot
    }
    val epsilon = 1e-6f
    val s0: Float
    val s1: Float
    if(1.0 - dot > epsilon) {
        val omega = acos(dot)
        val invSinOmega = 1.0f / Mth.sin(omega)
        s0 = Mth.sin((1.0f - alpha) * omega) * invSinOmega
        s1 = Mth.sin(alpha * omega) * invSinOmega
    } else {
        s0 = 1.0f - alpha
        s1 = alpha
    }

    return Vec2(s0 * beginX + s1 * endX, s0 * beginY + s1 * endY)
}