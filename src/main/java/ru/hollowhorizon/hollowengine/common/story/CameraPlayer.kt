package ru.hollowhorizon.hollowengine.common.story

import com.mojang.math.Vector3d
import com.mojang.math.Vector3f
import net.minecraft.client.Minecraft
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.util.Mth
import net.minecraft.world.phys.Vec2
import net.minecraftforge.common.util.INBTSerializable
import ru.hollowhorizon.hc.client.handlers.ClientTickHandler
import ru.hollowhorizon.hc.client.math.Spline3D
import ru.hollowhorizon.hc.client.utils.math.Interpolation
import ru.hollowhorizon.hollowengine.client.camera.CameraPath
import thedarkcolour.kotlinforforge.forge.vectorutil.minus
import thedarkcolour.kotlinforforge.forge.vectorutil.plus
import kotlin.math.acos

open class CameraPlayer {
    var maxTime: Int = 0
    var path: CameraNode = SimpleNode()
    private var progress = 0f
    private var startTime = 0

    fun update(): Container {
        progress = ClientTickHandler.ticks - startTime + Minecraft.getInstance().partialTick
        if (progress >= maxTime) onEnd()
        return Container(
            path.updatePosition(progress / maxTime),
            path.updateRotation(progress / maxTime)
        )
    }

    fun reset() {
        progress = 0f
        startTime = ClientTickHandler.ticks
    }

    open fun onEnd() {

    }

    data class Container(val point: Vector3d, val rotation: Vector3f)
}

interface CameraNode {
    val lastPos: Vector3d

    fun updateRotation(progress: Float): Vector3f
    fun updatePosition(progress: Float): Vector3d
}


class SplineNode(
    var cameraPath: CameraPath,
    var interpolation: Interpolation = Interpolation.LINEAR
) : CameraNode {
    private var spline3D = Spline3D(cameraPath.positions, cameraPath.rotations)
    override val lastPos: Vector3d get() = spline3D.getPoint(1.0)

    override fun updateRotation(progress: Float) = spline3D.getRotation(interpolation.function(progress).toDouble())
    override fun updatePosition(progress: Float) = spline3D.getPoint(interpolation.function(progress).toDouble())
}

class SimpleNode(
    private var begin: Vector3d = Vector3d(0.0, 0.0, 0.0),
    private var end: Vector3d = Vector3d(0.0, 0.0, 0.0),
    private var beginRot: Vec2 = Vec2.ZERO,
    private var endRot: Vec2 = Vec2.ZERO,
    val interpolation: (Float) -> Float = { progress -> progress }
) : CameraNode {
    override val lastPos: Vector3d get() = end

    override fun updateRotation(progress: Float) = Vector3f().apply {
        val r = beginRot.lerp(endRot, interpolation(progress))
        set(
            r.x,
            r.y,
            0f
        )
    }

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
    if (dot < 0) {
        endX = -endX
        endY = -endY
        dot = -dot
    }
    val epsilon = 1e-6f
    val s0: Float
    val s1: Float
    if (1.0 - dot > epsilon) {
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