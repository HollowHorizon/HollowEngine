package ru.hollowhorizon.hollowengine.client.render

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.renderer.LightTexture
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.culling.Frustum
import net.minecraft.world.phys.Vec3
import ru.hollowhorizon.hollowengine.client.models.internal.rendering.InstanceBatchManager
import ru.hollowhorizon.hollowengine.client.render.RenderManager.flushNodeBatches
import ru.hollowhorizon.hollowengine.client.render.RenderManager.renderNodeModels

object IrisRenderManager {
    private fun renderNodeShadowCasters(
        partialTick: Float,
        poseStack: PoseStack,
        bufferSource: MultiBufferSource,
        cameraPosition: Vec3,
        frustum: Frustum?,
        packedLight: Int,
        allowInstancing: Boolean,
    ) {
        val renderStats = renderNodeModels(
            partialTick = partialTick,
            poseStack = poseStack,
            bufferSource = bufferSource,
            cameraPosition = cameraPosition,
            frustum = frustum,
            packedLight = packedLight,
            allowInstancing = allowInstancing,
        )
        flushNodeBatches(bufferSource, renderStats)
        if (allowInstancing) InstanceBatchManager.flush()
    }

    fun renderIrisShadowCasters(
        modelView: PoseStack,
        bufferSource: MultiBufferSource,
        partialTick: Float,
        frustum: Frustum?,
        cameraX: Double,
        cameraY: Double,
        cameraZ: Double,
    ) {
        renderNodeShadowCasters(
            partialTick = partialTick,
            poseStack = modelView,
            bufferSource = bufferSource,
            cameraPosition = Vec3(cameraX, cameraY, cameraZ),
            frustum = frustum,
            packedLight = LightTexture.FULL_BRIGHT,
            allowInstancing = true,
        )
    }
}
