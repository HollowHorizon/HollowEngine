package ru.hollowhorizon.hollowengine.client.models.internal

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.resources.ResourceLocation

data class Mesh(
    val primitives: List<Primitive>,
    val weights: FloatArray,
) {
    val useBatching = primitives.any { it.useBatching }

    fun transformSkinning(node: Node) {
        primitives.asSequence().filter { it.hasSkinning }.forEach { it.transformSkinning(node) }
    }

    fun renderVAO(
        stack: PoseStack,
    ) {
        primitives.forEach {
            it.setWeights(weights)
            it.renderVAO(stack)
        }
    }

    fun renderBatching(stack: PoseStack, bufferSource: MultiBufferSource, overlayCoords: Int, packedLight: Int) {
        primitives.asSequence().filter { it.useBatching }.forEach {
            it.renderBatching(stack, bufferSource, overlayCoords, packedLight)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Mesh

        if (primitives != other.primitives) return false
        if (!weights.contentEquals(other.weights)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = primitives.hashCode()
        result = 31 * result + weights.contentHashCode()
        return result
    }
}