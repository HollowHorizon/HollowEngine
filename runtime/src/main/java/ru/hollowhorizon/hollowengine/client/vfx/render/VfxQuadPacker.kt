package ru.hollowhorizon.hollowengine.client.vfx.render

import org.joml.Matrix4f
import ru.hollowhorizon.hollowengine.client.vfx.VfxEmitter
import ru.hollowhorizon.hollowengine.client.vfx.render.VfxQuadPacker.Companion.STRIDE
import ru.hollowhorizon.hollowengine.common.utils.math.MutableVec3f
import ru.hollowhorizon.hollowengine.common.vfx.VfxBlend
import ru.hollowhorizon.hollowengine.common.vfx.VfxFacing
import ru.hollowhorizon.hollowengine.common.vfx.VfxMaterialSpec
import ru.hollowhorizon.hollowengine.common.vfx.VfxQuadEmitterSpec
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * How a batch is blended into the frame.
 *
 * [MIXED] is the one that lets different blend modes share a draw: with premultiplied alpha and a
 * `ONE, ONE_MINUS_SRC_ALPHA` blend, an alpha-blended particle writes its alpha, an additive one
 * writes zero alpha, and an opaque one writes one, so the three differ per particle rather than per
 * draw. Only multiplying needs a blend function of its own. A shader pack does its own output, so
 * with one active every mode keeps a batch of its own.
 */
internal enum class VfxBatchBlend {
    MIXED, OPAQUE, BLEND, ADDITIVE, MULTIPLY,
}

/** What decides whether two emitters can share a draw call. */
internal data class VfxBatchKey(
    val texture: String,
    val blend: VfxBatchBlend,
    val cull: Boolean,
    val depthTest: Boolean,
    val depthWrite: Boolean,
) {
    companion object {
        fun of(material: VfxMaterialSpec, mixed: Boolean) = VfxBatchKey(
            texture = material.texture,
            blend = when {
                material.blend == VfxBlend.MULTIPLY -> VfxBatchBlend.MULTIPLY
                mixed -> VfxBatchBlend.MIXED
                material.blend == VfxBlend.OPAQUE -> VfxBatchBlend.OPAQUE
                material.blend == VfxBlend.ADDITIVE -> VfxBatchBlend.ADDITIVE
                else -> VfxBatchBlend.BLEND
            },
            cull = material.cull,
            depthTest = material.depthTest,
            depthWrite = material.depthWrite,
        )
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
        get() = key.blend == VfxBatchBlend.BLEND || key.blend == VfxBatchBlend.MIXED && draws.any { (it.emitter.spec as VfxQuadEmitterSpec).material.blend == VfxBlend.BLEND }
}

/**
 * Turns the collected emitters into instance data, grouped into batches and in draw order.
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
    private val axis = MutableVec3f()
    private val edges = FloatArray(6)

    /** Packs [draws] as seen from [view]; returns how many particles there are in total. */
    fun pack(draws: List<VfxQuadDraw>, view: VfxView, mixed: Boolean): Int {
        byKey.clear()
        draws.forEach { draw ->
            val spec = draw.emitter.spec as? VfxQuadEmitterSpec ?: return@forEach
            val key = VfxBatchKey.of(spec.material, mixed)
            byKey.getOrPut(key) { VfxQuadBatch(key) }.draws += draw
        }

        var total = 0
        byKey.values.forEach { batch ->
            batch.count = batch.draws.sumOf { it.emitter.particles.count }
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
            val emitter = draw.emitter
            for (slot in 0 until emitter.particles.count) {
                write(target, offset, emitter, slot, draw.matrix, view)
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

    private fun write(out: FloatArray, at: Int, emitter: VfxEmitter, slot: Int, matrix: Matrix4f, view: VfxView) {
        val spec = emitter.spec as VfxQuadEmitterSpec
        val particles = emitter.particles

        val px = particles.positionX[slot]
        val py = particles.positionY[slot]
        val pz = particles.positionZ[slot]
        val cx = matrix.m00() * px + matrix.m10() * py + matrix.m20() * pz + matrix.m30()
        val cy = matrix.m01() * px + matrix.m11() * py + matrix.m21() * pz + matrix.m31()
        val cz = matrix.m02() * px + matrix.m12() * py + matrix.m22() * pz + matrix.m32()

        val width = particles.sizeX[slot]
        val height = particles.sizeY[slot]
        when (spec.facing) {
            VfxFacing.CAMERA -> cameraEdges(edges, matrix, view, particles.rotationZ[slot], width, height)
            VfxFacing.CAMERA_AXIS -> axisEdges(edges, emitter, spec, matrix, view, cx, cy, cz, width, height)
            VfxFacing.NONE -> rotatedEdges(
                edges, matrix,
                particles.rotationX[slot], particles.rotationY[slot], particles.rotationZ[slot],
                width, height,
            )
        }

        val region = spec.material.uv
        val columns = emitter.uvColumns
        val rows = emitter.uvRows
        val cellWidth = region.width / columns
        val cellHeight = region.height / rows
        val frame = particles.frame[slot].toInt().coerceIn(0, columns * rows - 1)
        val light = particles.light[slot]

        out[at] = cx
        out[at + 1] = cy
        out[at + 2] = cz
        System.arraycopy(edges, 0, out, at + 3, 6)
        out[at + 9] = particles.colorR[slot]
        out[at + 10] = particles.colorG[slot]
        out[at + 11] = particles.colorB[slot]
        out[at + 12] = particles.colorA[slot]
        out[at + 13] = region.u0 + (frame % columns) * cellWidth
        out[at + 14] = region.v0 + (frame / columns) * cellHeight
        out[at + 15] = cellWidth
        out[at + 16] = cellHeight
        out[at + 17] = (light and 0xFFFF).toFloat()
        out[at + 18] = (light shr 16 and 0xFFFF).toFloat()
        out[at + 19] = blendMode(spec.material.blend)
    }

    private fun cameraEdges(
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

    private fun axisEdges(
        out: FloatArray,
        emitter: VfxEmitter,
        spec: VfxQuadEmitterSpec,
        matrix: Matrix4f,
        view: VfxView,
        cx: Float,
        cy: Float,
        cz: Float,
        width: Float,
        height: Float,
    ) {
        emitter.toSimDirection(spec.facingAxis, axis)
        var ax = matrix.m00() * axis.x + matrix.m10() * axis.y + matrix.m20() * axis.z
        var ay = matrix.m01() * axis.x + matrix.m11() * axis.y + matrix.m21() * axis.z
        var az = matrix.m02() * axis.x + matrix.m12() * axis.y + matrix.m22() * axis.z
        val axisLength = sqrt(ax * ax + ay * ay + az * az)
        val scale: Float
        if (axisLength < EPSILON) {
            ax = view.up.x; ay = view.up.y; az = view.up.z
            scale = 1f
        } else {
            ax /= axisLength; ay /= axisLength; az /= axisLength
            scale = axisLength
        }

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

    private fun rotatedEdges(
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

    private fun ensureCapacity(count: Int) {
        if (packed.size >= count * STRIDE) return
        val capacity = Integer.highestOneBit((count - 1).coerceAtLeast(255)) * 2
        packed = FloatArray(capacity * STRIDE)
        staging = FloatArray(capacity * STRIDE)
        order = IntArray(capacity)
        depths = FloatArray(capacity)
    }

    companion object {
        /** Floats per particle: center, right edge, up edge, color, uv window, light, blend mode. */
        const val STRIDE = 20

        private const val EPSILON = 1.0e-6f
        private const val DEG_TO_RAD = (Math.PI / 180.0).toFloat()

        /** The blend mode as the particle shader reads it: 0 alpha, 1 additive, 2 opaque, 3 multiply. */
        fun blendMode(blend: VfxBlend): Float = when (blend) {
            VfxBlend.BLEND -> 0f
            VfxBlend.ADDITIVE -> 1f
            VfxBlend.OPAQUE -> 2f
            VfxBlend.MULTIPLY -> 3f
        }
    }
}
