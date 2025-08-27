package ru.hollowhorizon.hollowengine.client.models.internal

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.resources.ResourceLocation

data class Mesh(
    val primitives: List<Primitive>,
    val weights: FloatArray,
) {
    fun transformSkinning(node: Node) {
        primitives.filter { it.hasSkinning }.forEach { it.transformSkinning(node) }
    }

    fun render(
        node: Node,
        stack: PoseStack,
        consumer: (ResourceLocation) -> Int,
    ) {
        primitives.forEach {
            it.setWeights(weights)
            it.render(stack, node, consumer)
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