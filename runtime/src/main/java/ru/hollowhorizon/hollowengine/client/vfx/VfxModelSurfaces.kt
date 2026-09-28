package ru.hollowhorizon.hollowengine.client.vfx

import net.minecraft.resources.ResourceLocation
import ru.hollowhorizon.hollowengine.HollowEngine
import ru.hollowhorizon.hollowengine.client.models.internal.Model
import ru.hollowhorizon.hollowengine.client.models.internal.NodeDefinition
import ru.hollowhorizon.hollowengine.client.models.internal.manager.HollowModelManager
import ru.hollowhorizon.hollowengine.common.coroutines.scopeAsync
import ru.hollowhorizon.hollowengine.common.utils.math.Mat4f
import ru.hollowhorizon.hollowengine.common.utils.math.MutableMat4f
import ru.hollowhorizon.hollowengine.common.utils.math.MutableVec3f
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * The triangles of a model in its bind pose, ready to pick points on by area.
 */
class VfxModelSurface(
    private val corners: FloatArray,
    private val areas: FloatArray,
) {
    val isEmpty: Boolean get() = areas.isEmpty()

    /** A point spread evenly over the surface, and the outward normal of the triangle it is on. */
    fun sample(random: Random, position: MutableVec3f, normal: MutableVec3f) {
        if (isEmpty) return

        val wanted = random.nextFloat() * areas.last()
        var low = 0
        var high = areas.lastIndex
        while (low < high) {
            val middle = (low + high) ushr 1
            if (areas[middle] < wanted) low = middle + 1 else high = middle
        }

        var u = random.nextFloat()
        var v = random.nextFloat()
        if (u + v > 1f) {
            u = 1f - u
            v = 1f - v
        }
        val at = low * 9
        val ax = corners[at]
        val ay = corners[at + 1]
        val az = corners[at + 2]
        val abx = corners[at + 3] - ax
        val aby = corners[at + 4] - ay
        val abz = corners[at + 5] - az
        val acx = corners[at + 6] - ax
        val acy = corners[at + 7] - ay
        val acz = corners[at + 8] - az

        position.set(ax + abx * u + acx * v, ay + aby * u + acy * v, az + abz * u + acz * v)
        normal.set(aby * acz - abz * acy, abz * acx - abx * acz, abx * acy - aby * acx)
        if (normal.sqrLength() > 1.0e-12f) normal.norm()
    }

    companion object {
        /** Every triangle of every mesh of [model], each moved by the bind pose of its node. */
        fun of(model: Model): VfxModelSurface {
            val corners = ArrayList<Float>()
            val scene = model.scenes.getOrNull(model.scene) ?: model.scenes.firstOrNull()
            scene?.nodes?.forEach { root -> collect(root, Mat4f.IDENTITY, corners) }

            val packed = corners.toFloatArray()
            val areas = FloatArray(packed.size / 9)
            var total = 0f
            for (triangle in areas.indices) {
                total += area(packed, triangle * 9)
                areas[triangle] = total
            }
            return VfxModelSurface(packed, areas)
        }

        private fun collect(node: NodeDefinition, parent: Mat4f, into: MutableList<Float>) {
            val matrix = parent.mul(node.localMatrix, MutableMat4f())
            node.mesh?.primitives?.forEach { primitive ->
                val positions = primitive.positions ?: return@forEach
                val indices = primitive.indices ?: IntArray(positions.size) { it }
                val point = MutableVec3f()
                for (index in 0 until indices.size - indices.size % 3) {
                    val vertex = positions.getOrNull(indices[index]) ?: continue
                    matrix.transform(vertex, 1f, point)
                    into += point.x
                    into += point.y
                    into += point.z
                }
            }
            node.children.forEach { collect(it, matrix, into) }
        }

        private fun area(corners: FloatArray, at: Int): Float {
            val abx = corners[at + 3] - corners[at]
            val aby = corners[at + 4] - corners[at + 1]
            val abz = corners[at + 5] - corners[at + 2]
            val acx = corners[at + 6] - corners[at]
            val acy = corners[at + 7] - corners[at + 1]
            val acz = corners[at + 8] - corners[at + 2]
            val cx = aby * acz - abz * acy
            val cy = abz * acx - abx * acz
            val cz = abx * acy - aby * acx
            return sqrt(cx * cx + cy * cy + cz * cz) * 0.5f
        }
    }
}

/**
 * The model surfaces spawn shapes use, loaded once per model in the background.
 */
object VfxModelSurfaces {
    /** A surface on its way; [surface] is null until it has loaded, and stays null if it failed. */
    class Request {
        @Volatile
        var surface: VfxModelSurface? = null
            internal set
    }

    private val requests = HashMap<String, Request>()

    @Synchronized
    fun request(model: String): Request = requests.getOrPut(model) {
        Request().also { request -> load(model, request) }
    }

    /** Models change with resource packs; the next request loads them again. */
    @Synchronized
    fun clear() = requests.clear()

    private fun load(model: String, request: Request) {
        val location = ResourceLocation.tryParse(model) ?: return
        scopeAsync {
            try {
                request.surface = VfxModelSurface.of(HollowModelManager.loadModel(location))
            } catch (e: Exception) {
                HollowEngine.LOGGER.warn("Could not read the spawn shape model {}: {}", model, e.message)
            }
        }
    }
}
