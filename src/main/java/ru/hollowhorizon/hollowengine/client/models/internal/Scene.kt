package ru.hollowhorizon.hollowengine.client.models.internal

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.resources.ResourceLocation

data class Scene(
    val nodes: List<Node>,
) {
    fun renderVAO(
        stack: PoseStack
    ) {
        nodes.forEach { it.renderVAO(stack) }
    }

    fun renderBatching(
        stack: PoseStack,
        nodeRenderer: NodeRenderer,
        data: ModelData,
        bufferSource: MultiBufferSource,
        overlayCoords: Int,
        light: Int,
    ) {
        nodes.forEach { it.renderBatching(stack, nodeRenderer, data, bufferSource, overlayCoords, light) }
    }


    fun transformSkinning() {
        nodes.forEach { it.transformSkinning() }
    }
}