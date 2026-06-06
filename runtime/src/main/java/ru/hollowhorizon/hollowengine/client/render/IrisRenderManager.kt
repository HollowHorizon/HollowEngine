package ru.hollowhorizon.hollowengine.client.render

import com.mojang.blaze3d.vertex.PoseStack
import net.irisshaders.iris.mixin.LevelRendererAccessor
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.LightTexture
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.culling.Frustum
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.Vec3
import ru.hollowhorizon.hollowengine.client.render.RenderManager.flushNodeBatches
import ru.hollowhorizon.hollowengine.client.render.RenderManager.renderNodeModels

object IrisRenderManager {
    fun renderLocalShadowCasters(
        renderer: LevelRendererAccessor,
        modelView: PoseStack,
        cameraPosition: Vec3,
        partialTick: Float,
        frustum: Frustum?,
    ) {
        val minecraft = Minecraft.getInstance()
        val level = minecraft.level ?: return
        val bufferSource = renderer.renderBuffers.bufferSource()

        renderEntities(
            renderer = renderer,
            entities = level.entitiesForRendering(),
            modelView = modelView,
            bufferSource = bufferSource,
            cameraPosition = cameraPosition,
            partialTick = partialTick,
            frustum = frustum,
        )
        renderPlayerShadowIfNeeded(
            renderer = renderer,
            modelView = modelView,
            bufferSource = bufferSource,
            cameraPosition = cameraPosition,
            partialTick = partialTick,
            frustum = frustum,
        )
        renderNodeShadowCasters(
            partialTick = partialTick,
            poseStack = modelView,
            bufferSource = bufferSource,
            cameraPosition = cameraPosition,
            frustum = frustum,
            packedLight = LightTexture.FULL_BRIGHT,
            allowInstancing = false,
        )
    }

    private fun renderEntities(
        renderer: LevelRendererAccessor,
        entities: Iterable<Entity>,
        modelView: PoseStack,
        bufferSource: MultiBufferSource,
        cameraPosition: Vec3,
        partialTick: Float,
        frustum: Frustum?,
    ) {
        val minecraft = Minecraft.getInstance()
        val dispatcher = minecraft.entityRenderDispatcher

        entities.forEach { entity ->
            if (!entity.isAlive || entity.isSpectator) return@forEach
            if (!dispatcher.shouldRender(
                    entity,
                    frustum,
                    cameraPosition.x,
                    cameraPosition.y,
                    cameraPosition.z
                )
            ) return@forEach
            renderer.invokeRenderEntity(
                entity, cameraPosition.x, cameraPosition.y, cameraPosition.z, partialTick,
                modelView, bufferSource
            )
        }
    }

    private fun renderPlayerShadowIfNeeded(
        renderer: LevelRendererAccessor,
        modelView: PoseStack,
        bufferSource: MultiBufferSource,
        cameraPosition: Vec3,
        partialTick: Float,
        frustum: Frustum?,
    ) {
        val minecraft = Minecraft.getInstance()
        val dispatcher = minecraft.entityRenderDispatcher
        val player = minecraft.player ?: return
        if (player.isSpectator) return
        if (!dispatcher.shouldRender(player, frustum, cameraPosition.x, cameraPosition.y, cameraPosition.z)) return

        player.vehicle?.let { vehicle ->
            renderer.invokeRenderEntity(
                vehicle,
                cameraPosition.x,
                cameraPosition.y,
                cameraPosition.z,
                partialTick,
                modelView,
                bufferSource
            )
        }
        player.passengers.forEach { passenger ->
            renderer.invokeRenderEntity(
                passenger,
                cameraPosition.x,
                cameraPosition.y,
                cameraPosition.z,
                partialTick,
                modelView,
                bufferSource
            )
        }
        renderer.invokeRenderEntity(
            player,
            cameraPosition.x,
            cameraPosition.y,
            cameraPosition.z,
            partialTick,
            modelView,
            bufferSource
        )
    }

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
            allowInstancing = false,
        )
    }
}