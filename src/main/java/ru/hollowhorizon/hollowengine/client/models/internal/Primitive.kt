package ru.hollowhorizon.hollowengine.client.models.internal

import de.fabmax.kool.math.*
import ru.hollowhorizon.hollowengine.client.models.internal.rendering.BatchingRenderer
import ru.hollowhorizon.hollowengine.client.models.internal.rendering.MeshRenderer
import ru.hollowhorizon.hollowengine.client.models.internal.rendering.PipelineRenderer
import ru.hollowhorizon.hollowengine.client.models.internal.rendering.RenderPipeline
import ru.hollowhorizon.hollowengine.client.models.internal.utils.GeometryUtils
import kotlin.math.max
import kotlin.math.min

class Primitive(
    var positions: Array<Vec3f>? = null,
    var normals: Array<Vec3f>? = null,
    var texCoords: Array<Vec2f>? = null,
    var midCoords: Array<Vec2f>? = null,
    var tangents: Array<Vec4f>? = null,
    var joints: Array<Vec4i>? = null,
    var jointWeights: Array<Vec4f>? = null,
    val indices: IntArray? = null,
    val material: Material,
    val morphTargets: List<Map<String, FloatArray>> = listOf(),
    var weights: FloatArray = FloatArray(morphTargets.size) { 0f },
) {
    val hasSkinning = joints != null && jointWeights != null
    val positionsCount: Int get() = (positions?.size ?: 0) * 3
    var jointCount = 0

    val useBatching = positionsCount < 512 && !hasSkinning && morphTargets.isEmpty() && false

    val localBounds: Pair<Vec3f, Vec3f>? by lazy { computeBounds() }

    private var renderer: MeshRenderer? = null

    private var isLoaded = false

    fun init() {
        if (isLoaded) return
        isLoaded = true

        if (!useBatching) {
            if (normals == null && positions != null) {
                normals = GeometryUtils.recalculateNormals(indices, positions!!)
            }
            if (tangents == null && positions != null && texCoords != null && normals != null) {
                tangents = GeometryUtils.recalculateTangents(indices, positions!!, texCoords!!, normals!!)
            }
        }

        renderer = if (useBatching) {
            BatchingRenderer(this)
        } else {
            PipelineRenderer(this)
        }

        renderer?.init()

        if (!useBatching) releaseCpu()
    }

    fun setupPipeline(
        pipeline: RenderPipeline,
        skinGetter: SkinGetter,
        matrixGetter: MatrixGetter,
        visibilityGetter: VisibilityGetter,
    ) {
        init()
        renderer?.setupPipeline(pipeline, skinGetter, matrixGetter, visibilityGetter)
    }

    fun destroy() {
        renderer?.destroy()
    }

    private fun releaseCpu() {
        if (!useBatching) {
            positions = null
            normals = null
            texCoords = null
            midCoords = null
            tangents = null
            joints = null
            jointWeights = null
        }
    }

    private fun computeBounds(): Pair<Vec3f, Vec3f>? {
        val pos = positions ?: return null
        if (pos.isEmpty()) return null

        var min = MutableVec3f(Float.POSITIVE_INFINITY)
        var max = MutableVec3f(Float.NEGATIVE_INFINITY)

        pos.forEach {
            min.x = min(min.x, it.x); min.y = min(min.y, it.y); min.z = min(min.z, it.z)
            max.x = max(max.x, it.x); max.y = max(max.y, it.y); max.z = max(max.z, it.z)
        }
        return Vec3f(min) to Vec3f(max)
    }
}

typealias MatrixGetter = () -> Mat4f
typealias SkinGetter = () -> Array<Mat4f>
typealias VisibilityGetter = () -> Boolean