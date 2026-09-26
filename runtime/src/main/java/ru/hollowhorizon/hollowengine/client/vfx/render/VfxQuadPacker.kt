package ru.hollowhorizon.hollowengine.client.vfx.render

import net.minecraft.util.Mth
import org.joml.Matrix4f
import org.joml.Quaternionf
import ru.hollowhorizon.hollowengine.client.vfx.VfxParticleLook
import ru.hollowhorizon.hollowengine.client.vfx.render.VfxQuadPacker.Companion.STRIDE
import ru.hollowhorizon.hollowengine.common.vfx.VfxBlend
import ru.hollowhorizon.hollowengine.common.vfx.VfxFacing
import ru.hollowhorizon.hollowengine.common.vfx.VfxMaterialSpec
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * How a batch is blended into the frame.
 *
 * [MIXED] is the one that lets different blend modes share a draw: with premultiplied alpha and a
 * `ONE, ONE_MINUS_SRC_ALPHA` blend, an alpha-blended particle writes its alpha, an additive one
 * writes zero alpha, and an opaque one writes one, so the three differ per particle rather than per
 * draw. Only multiplying needs a blend function of its own. A shader of the author does not know
 * about the trick, so its batches keep a blend function per mode.
 */
internal enum class VfxBatchBlend {
    MIXED, OPAQUE, BLEND, ADDITIVE, MULTIPLY;

    companion object {
        fun of(blend: VfxBlend, mixed: Boolean) = when {
            blend == VfxBlend.MULTIPLY -> MULTIPLY
            mixed -> MIXED
            blend == VfxBlend.OPAQUE -> OPAQUE
            blend == VfxBlend.ADDITIVE -> ADDITIVE
            else -> BLEND
        }
    }
}

/**
 * What decides whether two draws can share a draw call.
 *
 * A draw with a shader of its own also carries uniforms of its own, so [owner] keeps it apart. Planes
 * of the engine program carry softness and glow per particle, so only the other draws are split by
 * them.
 */
internal data class VfxBatchKey(
    val texture: String,
    val blend: VfxBatchBlend,
    val cull: Boolean,
    val depthTest: Boolean,
    val depthWrite: Boolean,
    val shader: String? = null,
    val owner: Any? = null,
    val softness: Float = 0f,
    val glow: Float = 0f,
) {
    companion object {
        fun of(material: VfxMaterialSpec, mixed: Boolean, owner: Any?): VfxBatchKey {
            val custom = material.shader != null
            val perParticle = mixed && !custom
            return VfxBatchKey(
                texture = material.texture,
                blend = VfxBatchBlend.of(material.blend, perParticle),
                cull = material.cull,
                depthTest = material.depthTest,
                depthWrite = material.depthWrite,
                shader = material.shader,
                owner = if (custom) owner else null,
                softness = if (perParticle) 0f else material.softness,
                glow = if (perParticle) 0f else material.glow,
            )
        }
    }
}

/** One draw call, where its particles start in the packed data, how many, and how far away. */
internal class VfxQuadBatch(val key: VfxBatchKey) {
    val draws = ArrayList<VfxQuadDraw>()
    var first = 0
    var count = 0
    var depth = 0f

    /** Whether anything in the batch is alpha blended, which is what makes its order matter. */
    val sorted: Boolean
        get() = key.blend == VfxBatchBlend.BLEND ||
                key.blend == VfxBatchBlend.MIXED && draws.any { it.plane.material.blend == VfxBlend.BLEND }

    /** The uniforms of the one draw a batch with a shader of its own holds. */
    val uniforms: VfxUniformValues? get() = draws.firstOrNull()?.uniforms

    /** Whether anything in the batch glows, which is what draws it again in the glow pass. */
    val glows: Boolean get() = draws.any { it.plane.material.glow > 0f }
}

/**
 * Turns the collected planes into instance data, grouped into batches and in draw order.
 */
internal class VfxQuadPacker {
    /** Particles written in draw order, [STRIDE] floats each. */
    var packed = FloatArray(STRIDE * 256)
        private set

    val batches = ArrayList<VfxQuadBatch>()

    private val byKey = LinkedHashMap<VfxBatchKey, VfxQuadBatch>()
    private var staging = FloatArray(STRIDE * 256)
    private var order = IntArray(256)
    private var depths = FloatArray(256)
    private val edges = FloatArray(6)

    /** Packs [draws] as seen from [view]; returns how many particles there are in total. */
    fun pack(draws: List<VfxQuadDraw>, view: VfxView): Int {
        byKey.clear()
        batches.clear()
        if (draws.isEmpty()) return 0
        draws.forEach { draw ->
            val key = VfxBatchKey.of(draw.plane.material, mixed = true, owner = draw)
            byKey.getOrPut(key) { VfxQuadBatch(key) }.draws += draw
        }

        var total = 0
        byKey.values.forEach { batch ->
            batch.count = batch.draws.sumOf { it.batch.particles.count }
            total += batch.count
        }
        batches.clear()
        if (total == 0) return 0
        ensureCapacity(total)

        var cursor = 0
        byKey.values.forEach { batch ->
            batch.first = cursor
            fill(batch, view)
            cursor += batch.count
        }

        byKey.values.filterTo(batches) { !it.sorted && it.count > 0 }
        byKey.values.filter { it.sorted && it.count > 0 }.sortedByDescending { it.depth }.forEach(batches::add)
        return total
    }

    private fun fill(batch: VfxQuadBatch, view: VfxView) {
        val sorted = batch.sorted
        val target = if (sorted) staging else packed
        var offset = if (sorted) 0 else batch.first * STRIDE
        var depthSum = 0f

        batch.draws.forEach { draw ->
            for (slot in 0 until draw.batch.particles.count) {
                write(target, offset, draw, slot, view)
                if (sorted) {
                    val index = offset / STRIDE
                    val depth = distanceSquared(target, offset, view)
                    depths[index] = depth
                    order[index] = index
                    depthSum += depth
                }
                offset += STRIDE
            }
        }

        if (!sorted) return
        batch.depth = if (batch.count > 0) depthSum / batch.count else 0f

        for (index in 1 until batch.count) {
            val item = order[index]
            val depth = depths[item]
            var scan = index - 1
            while (scan >= 0 && depths[order[scan]] < depth) {
                order[scan + 1] = order[scan]
                scan--
            }
            order[scan + 1] = item
        }
        for (position in 0 until batch.count) {
            System.arraycopy(staging, order[position] * STRIDE, packed, (batch.first + position) * STRIDE, STRIDE)
        }
    }

    private fun distanceSquared(data: FloatArray, offset: Int, view: VfxView): Float {
        val dx = data[offset] - view.eye.x
        val dy = data[offset + 1] - view.eye.y
        val dz = data[offset + 2] - view.eye.z
        return dx * dx + dy * dy + dz * dz
    }

    private fun write(out: FloatArray, base: Int, draw: VfxQuadDraw, slot: Int, view: VfxView) {
        val plane = draw.plane
        val batch = draw.batch
        val particles = batch.particles
        val matrix = batch.matrix

        val px = particles.positionX[slot] + batch.offset.x
        val py = particles.positionY[slot] + batch.offset.y
        val pz = particles.positionZ[slot] + batch.offset.z
        val cx = matrix.m00() * px + matrix.m10() * py + matrix.m20() * pz + matrix.m30()
        val cy = matrix.m01() * px + matrix.m11() * py + matrix.m21() * pz + matrix.m31()
        val cz = matrix.m02() * px + matrix.m12() * py + matrix.m22() * pz + matrix.m32()

        val look = batch.look
        val at = batch.lookOf(slot)
        val width = look[at + VfxParticleLook.SIZE] * batch.sizeScale.x
        val height = look[at + VfxParticleLook.SIZE + 1] * batch.sizeScale.y
        val roll = look[at + VfxParticleLook.ROTATION + 2] + batch.spin.z
        when (plane.facing) {
            VfxFacing.CAMERA -> VfxBillboards.cameraEdges(edges, matrix, view, roll, width, height)
            VfxFacing.CAMERA_AXIS -> VfxBillboards.axisEdges(
                edges, matrix, view, cx, cy, cz,
                plane.facingAxis.x, plane.facingAxis.y, plane.facingAxis.z,
                width, height,
            )

            VfxFacing.VELOCITY -> VfxBillboards.axisEdges(
                edges, matrix, view, cx, cy, cz,
                particles.velocityX[slot], particles.velocityY[slot], particles.velocityZ[slot],
                width, height,
            )

            VfxFacing.NONE -> VfxBillboards.rotatedEdges(
                edges, matrix,
                look[at + VfxParticleLook.ROTATION] + batch.spin.x, look[at + VfxParticleLook.ROTATION + 1] + batch.spin.y, roll,
                width, height,
            )
        }

        val region = plane.material.uv
        val columns = batch.uvColumns
        val rows = batch.uvRows
        val cellWidth = region.width / columns
        val cellHeight = region.height / rows
        val frame = particles.frame[slot].toInt().coerceIn(0, columns * rows - 1)
        val light = particles.light[slot]
        val tint = batch.tint

        val color = at + VfxParticleLook.COLOR
        out[base] = cx
        out[base + 1] = cy
        out[base + 2] = cz
        System.arraycopy(edges, 0, out, base + 3, 6)
        out[base + 9] = look[color] * tint[0]
        out[base + 10] = look[color + 1] * tint[1]
        out[base + 11] = look[color + 2] * tint[2]
        out[base + 12] = look[color + 3] * tint[3]
        out[base + 13] = region.u0 + (frame % columns) * cellWidth
        out[base + 14] = region.v0 + (frame / columns) * cellHeight
        out[base + 15] = cellWidth
        out[base + 16] = cellHeight
        out[base + 17] = (light and 0xFFFF).toFloat()
        out[base + 18] = (light shr 16 and 0xFFFF).toFloat()
        out[base + 19] = blendMode(plane.material.blend)
        out[base + 20] = plane.material.softness
        out[base + 21] = plane.material.glow
    }

    private fun ensureCapacity(count: Int) {
        if (packed.size >= count * STRIDE) return
        val capacity = Integer.highestOneBit((count - 1).coerceAtLeast(255)) * 2
        packed = FloatArray(capacity * STRIDE)
        staging = FloatArray(capacity * STRIDE)
        order = IntArray(capacity)
        depths = FloatArray(capacity)
    }

    companion object {
        /** Floats per particle: center, right edge, up edge, color, uv window, light, blend mode, softness, glow. */
        const val STRIDE = 22

        /** The blend mode as the particle shader reads it: 0 alpha, 1 additive, 2 opaque, 3 multiply. */
        fun blendMode(blend: VfxBlend): Float = when (blend) {
            VfxBlend.BLEND -> 0f
            VfxBlend.ADDITIVE -> 1f
            VfxBlend.OPAQUE -> 2f
            VfxBlend.MULTIPLY -> 3f
        }
    }
}

/**
 * The two edge vectors of a quad in the space of the view, however it faces the camera.
 */
internal object VfxBillboards {
    private const val EPSILON = 1.0e-6f
    private const val DEG_TO_RAD = (Math.PI / 180.0).toFloat()

    /** Turns a velocity into a rotation that points a mesh or a model along it. */
    fun facing(x: Float, y: Float, z: Float): Quaternionf {
        if (x * x + y * y + z * z < EPSILON) return Quaternionf()
        val yaw = Mth.atan2(x.toDouble(), z.toDouble()).toFloat()
        val pitch = Mth.atan2(y.toDouble(), Mth.sqrt(x * x + z * z).toDouble()).toFloat()
        return Quaternionf().rotateY(yaw).rotateX(-pitch)
    }

    fun cameraEdges(
        out: FloatArray,
        matrix: Matrix4f,
        view: VfxView,
        rollDegrees: Float,
        width: Float,
        height: Float,
    ) {
        val scale = sqrt(matrix.m00() * matrix.m00() + matrix.m01() * matrix.m01() + matrix.m02() * matrix.m02())
        val roll = rollDegrees * DEG_TO_RAD
        val c = cos(roll)
        val s = sin(roll)
        val w = width * scale
        val h = height * scale
        out[0] = (view.right.x * c + view.up.x * s) * w
        out[1] = (view.right.y * c + view.up.y * s) * w
        out[2] = (view.right.z * c + view.up.z * s) * w
        out[3] = (view.up.x * c - view.right.x * s) * h
        out[4] = (view.up.y * c - view.right.y * s) * h
        out[5] = (view.up.z * c - view.right.z * s) * h
    }

    /**
     * A quad along an axis given in the space of the particles, turned around it to face the
     * camera. With no axis to speak of, it simply faces the camera.
     */
    fun axisEdges(
        out: FloatArray,
        matrix: Matrix4f,
        view: VfxView,
        cx: Float,
        cy: Float,
        cz: Float,
        axisX: Float,
        axisY: Float,
        axisZ: Float,
        width: Float,
        height: Float,
    ) {
        var ax = matrix.m00() * axisX + matrix.m10() * axisY + matrix.m20() * axisZ
        var ay = matrix.m01() * axisX + matrix.m11() * axisY + matrix.m21() * axisZ
        var az = matrix.m02() * axisX + matrix.m12() * axisY + matrix.m22() * axisZ
        val axisLength = sqrt(ax * ax + ay * ay + az * az)
        if (axisLength < EPSILON) {
            cameraEdges(out, matrix, view, 0f, width, height)
            return
        }
        ax /= axisLength
        ay /= axisLength
        az /= axisLength
        val scale = sqrt(matrix.m00() * matrix.m00() + matrix.m01() * matrix.m01() + matrix.m02() * matrix.m02())

        val tx = view.eye.x - cx
        val ty = view.eye.y - cy
        val tz = view.eye.z - cz
        var rx = ay * tz - az * ty
        var ry = az * tx - ax * tz
        var rz = ax * ty - ay * tx
        val rightLength = sqrt(rx * rx + ry * ry + rz * rz)
        if (rightLength < EPSILON) {
            rx = view.right.x; ry = view.right.y; rz = view.right.z
        } else {
            rx /= rightLength; ry /= rightLength; rz /= rightLength
        }
        out[0] = rx * width * scale
        out[1] = ry * width * scale
        out[2] = rz * width * scale
        out[3] = ax * height * scale
        out[4] = ay * height * scale
        out[5] = az * height * scale
    }

    fun rotatedEdges(
        out: FloatArray,
        matrix: Matrix4f,
        degreesX: Float,
        degreesY: Float,
        degreesZ: Float,
        width: Float,
        height: Float,
    ) {
        val sx = sin(degreesX * DEG_TO_RAD)
        val cx = cos(degreesX * DEG_TO_RAD)
        val sy = sin(degreesY * DEG_TO_RAD)
        val cy = cos(degreesY * DEG_TO_RAD)
        val sz = sin(degreesZ * DEG_TO_RAD)
        val cz = cos(degreesZ * DEG_TO_RAD)

        val lx = cz * cy * width
        val ly = sz * cy * width
        val lz = -sy * width
        val vx = (cz * sy * sx - sz * cx) * height
        val vy = (sz * sy * sx + cz * cx) * height
        val vz = cy * sx * height

        out[0] = matrix.m00() * lx + matrix.m10() * ly + matrix.m20() * lz
        out[1] = matrix.m01() * lx + matrix.m11() * ly + matrix.m21() * lz
        out[2] = matrix.m02() * lx + matrix.m12() * ly + matrix.m22() * lz
        out[3] = matrix.m00() * vx + matrix.m10() * vy + matrix.m20() * vz
        out[4] = matrix.m01() * vx + matrix.m11() * vy + matrix.m21() * vz
        out[5] = matrix.m02() * vx + matrix.m12() * vy + matrix.m22() * vz
    }
}
