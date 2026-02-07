package ru.hollowhorizon.hollowengine.client.models.internal.rendering

import com.mojang.blaze3d.vertex.VertexConsumer
import de.fabmax.kool.math.MutableMat3f
import de.fabmax.kool.math.MutableVec3f
import de.fabmax.kool.util.Color
import org.joml.Matrix3f
import org.joml.Matrix4f
import ru.hollowhorizon.hollowengine.client.models.internal.*

class BatchingRenderer(
    private val primitive: Primitive
) : MeshRenderer {

    override fun init() {
        // No GL initialization needed for batching
    }

    override fun setupPipeline(
        pipeline: RenderPipeline,
        skinGetter: SkinGetter,
        matrixGetter: MatrixGetter,
        visibilityGetter: VisibilityGetter
    ) {
        val indices = primitive.indices
        val iterator = indices?.asIterable() ?: (0 until primitive.positionsCount / 3)

        pipeline.addBatchedRenderable {
            if (!visibilityGetter()) return@addBatchedRenderable

            val renderType = batchingRenderType.apply(primitive.material)
            val vertexConsumer = source.getBuffer(renderType)
            val pose = stack.last().pose()
            val normal = stack.last().normal()
            val color = primitive.material.color

            for (i in iterator) {
                putVertex(matrixGetter, i, vertexConsumer, pose, normal, color, overlay, light)
            }
        }
    }

    private fun putVertex(
        getter: MatrixGetter,
        index: Int,
        consumer: VertexConsumer,
        pose: Matrix4f,
        normalMat: Matrix3f,
        color: Color,
        overlayCoords: Int,
        packedLight: Int
    ) {
        val posArray = primitive.positions ?: return
        val normArray = primitive.normals ?: return
        val texArray = primitive.texCoords ?: return

        val global = getter()
        val pos = global.transform(posArray[index], 1f, MutableVec3f())
        val normal = global.getUpperLeft(MutableMat3f()).transform(normArray[index], MutableVec3f())

        consumer
            .vertex(pose, pos.x, pos.y, pos.z)
            .color(color.r, color.g, color.b, color.a)
            .uv(texArray[index].x, texArray[index].y)
            .overlayCoords(overlayCoords)
            .uv2(packedLight)
            .normal(normalMat, normal.x, normal.y, normal.z)
            .endVertex()
    }

    override fun destroy() {
        // Nothing to destroy
    }
}