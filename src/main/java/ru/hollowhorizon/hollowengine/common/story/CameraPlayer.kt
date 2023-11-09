package ru.hollowhorizon.hollowengine.common.story

import com.mojang.math.Vector3d
import net.minecraft.client.Minecraft
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.util.Mth
import net.minecraft.world.phys.Vec2
import net.minecraftforge.common.util.INBTSerializable
import ru.hollowhorizon.hc.client.handlers.ClientTickHandler
import ru.hollowhorizon.hc.client.math.Spline3D
import thedarkcolour.kotlinforforge.forge.vectorutil.minus
import thedarkcolour.kotlinforforge.forge.vectorutil.plus
import kotlin.math.acos

open class CameraPlayer(
    vararg var pairs: Pair<Int, CameraNode>,
): INBTSerializable<CompoundTag> {
    private var progress = 0f
    private var startTime = 0
    private var nodeIndex = 0

    fun update(): Container {
        val (maxTime, node) = pairs[nodeIndex]
        progress = ClientTickHandler.ticks - startTime + Minecraft.getInstance().partialTick
        if (progress >= maxTime) {
            startTime = ClientTickHandler.ticks
            nodeIndex++
        }
        if (nodeIndex >= pairs.size) {
            nodeIndex = 0
            onEnd()
        }
        return Container(
            node.updatePosition(progress / maxTime),
            node.updateRotation(progress / maxTime)
        )
    }

    fun reset() {
        progress = 0f
        nodeIndex = 0
        startTime = ClientTickHandler.ticks
    }

    open fun onEnd() {

    }

    data class Container(val point: Vector3d, val rotation: Vec2)

    override fun serializeNBT() = CompoundTag().apply {
        put("pairs", ListTag().apply {
            addAll(pairs.map { p ->
                CompoundTag().apply {
                    putInt("time", p.first)
                    put("node", p.second.serializeNBT())
                    putBoolean("isSpline", p.second is SplineNode)
                }
            })
        })
        putFloat("progress", progress)
        putInt("nodeIndex", nodeIndex)
    }

    override fun deserializeNBT(nbt: CompoundTag) {
        val pairs = nbt.getList("pairs", 10).map { it as CompoundTag }.map { p ->
            val isSpline = p.getBoolean("isSpline")
            val node = if (isSpline) SplineNode() else SimpleNode()
            Pair(p.getInt("time"), node.apply { deserializeNBT(p.getCompound("node")) })
        }
        this.pairs = pairs.toTypedArray()
        progress = nbt.getFloat("progress")
        nodeIndex = nbt.getInt("nodeIndex")
    }
}

interface CameraNode : INBTSerializable<CompoundTag> {
    val lastPos: Vector3d
    fun updateRotation(progress: Float): Vec2
    fun updatePosition(progress: Float): Vector3d
}


class SplineNode(
    private var beginRot: Vec2 = Vec2.ZERO,
    private var endRot: Vec2 = Vec2.ZERO,
    private vararg val points: Vector3d = arrayOf(Vector3d(1.0, 2.0, 3.0), Vector3d(1.0, 2.0, 3.0), Vector3d(1.0, 2.0, 3.0)),
    val interpolation: (Float) -> Float = { progress -> progress }
) : CameraNode {
    private var spline3D = Spline3D(points.toList())
    override val lastPos: Vector3d get() = spline3D.getPoint(1.0)

    override fun updateRotation(progress: Float) = beginRot.lerp(endRot, interpolation(progress))
    override fun updatePosition(progress: Float) = spline3D.getPoint(interpolation(progress).toDouble())

    override fun serializeNBT() = CompoundTag().apply {
        put("points", ListTag().apply {
            addAll(points.map { p ->
                CompoundTag().apply {
                    putDouble("x", p.x)
                    putDouble("y", p.y)
                    putDouble("z", p.z)
                }
            })
        })
        putFloat("beginRotX", beginRot.x)
        putFloat("beginRotY", beginRot.y)
        putFloat("endRotX", endRot.x)
        putFloat("endRotY", endRot.y)
    }

    override fun deserializeNBT(nbt: CompoundTag) {
        val points = nbt.getList("points", 10).map { it as CompoundTag }.map { p ->
            Vector3d(p.getDouble("x"), p.getDouble("y"), p.getDouble("z"))
        }
        spline3D = Spline3D(points)
        beginRot = Vec2(nbt.getFloat("beginRotX"), nbt.getFloat("beginRotY"))
        endRot = Vec2(nbt.getFloat("endRotX"), nbt.getFloat("endRotY"))
    }
}

class SimpleNode(
    private var begin: Vector3d = Vector3d(0.0, 0.0, 0.0),
    private var end: Vector3d = Vector3d(0.0, 0.0, 0.0),
    private var beginRot: Vec2 = Vec2.ZERO,
    private var endRot: Vec2 = Vec2.ZERO,
    val interpolation: (Float) -> Float = { progress -> progress }
) : CameraNode {
    override val lastPos: Vector3d get() = end

    override fun updateRotation(progress: Float) = beginRot.lerp(endRot, interpolation(progress))

    override fun updatePosition(progress: Float) = interpolation(progress) * (end - begin) + begin
    override fun serializeNBT() = CompoundTag().apply {
        putDouble("bx", begin.x)
        putDouble("by", begin.y)
        putDouble("bz", begin.z)
        putDouble("ex", begin.x)
        putDouble("ey", begin.y)
        putDouble("ez", begin.z)
        putFloat("beginRotX", beginRot.x)
        putFloat("beginRotY", beginRot.y)
        putFloat("endRotX", endRot.x)
        putFloat("endRotY", endRot.y)
    }

    override fun deserializeNBT(nbt: CompoundTag) {
        begin = Vector3d(nbt.getDouble("bx"), nbt.getDouble("by"), nbt.getDouble("bz"))
        end = Vector3d(nbt.getDouble("ex"), nbt.getDouble("ey"), nbt.getDouble("ez"))
        beginRot = Vec2(nbt.getFloat("beginRotX"), nbt.getFloat("beginRotY"))
        endRot = Vec2(nbt.getFloat("endRotX"), nbt.getFloat("endRotY"))
    }
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