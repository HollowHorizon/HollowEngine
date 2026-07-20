package ru.hollowhorizon.hollowengine.client.models.internal.v2

import ru.hollowhorizon.hollowengine.client.models.internal.Material
import ru.hollowhorizon.hollowengine.client.models.internal.Primitive
import ru.hollowhorizon.hollowengine.client.models.internal.rendering.RenderPipeline
import ru.hollowhorizon.hollowengine.common.utils.math.Mat4f

class PrimitiveInstance(
    val primitive: Primitive,
    private val node: RuntimeNode,
    val material: Material,
) {
    val matrix: Mat4f get() = node.globalMatrix
    val morphWeights: FloatArray get() = node.morphWeights
    val isVisible: Boolean get() = node.isVisible

    fun skinMatrices(): Array<Mat4f> =
        node.definition.skin!!.compute(node.globalMatrix, node.jointGetter)

    fun setupPipeline(pipeline: RenderPipeline) {
        primitive.setupPipeline(pipeline, this)
    }
}
