package ru.hollowhorizon.hollowengine.client.render

import com.mojang.blaze3d.vertex.PoseStack
import de.fabmax.kool.math.QuatF
import de.fabmax.kool.math.Vec3f
import net.minecraft.client.Minecraft
import net.minecraft.client.CameraType
import net.minecraft.client.renderer.LightTexture
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.culling.Frustum
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.util.Mth
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.Vec3
import org.joml.Quaternionf
import ru.hollowhorizon.hollowengine.api.system
import ru.hollowhorizon.hollowengine.client.handlers.TickHandler
import ru.hollowhorizon.hollowengine.client.kool.KoolManager
import ru.hollowhorizon.hollowengine.client.models.internal.manager.HollowModelManager
import ru.hollowhorizon.hollowengine.client.models.internal.rendering.InstanceBatchManager
import ru.hollowhorizon.hollowengine.client.models.internal.rendering.RenderContext
import ru.hollowhorizon.hollowengine.client.particles.ParticleVertexConsumerProvider
import ru.hollowhorizon.hollowengine.client.render.lighting.ClusteredLightingManager
import net.irisshaders.iris.mixin.LevelRendererAccessor
import ru.hollowhorizon.hollowengine.common.events.SubscribeEvent
import ru.hollowhorizon.hollowengine.common.events.client.render.RenderLevelStageEvent
import ru.hollowhorizon.hollowengine.common.events.client.render.RenderStage
import ru.hollowhorizon.hollowengine.common.geary.anchor.EntityAnchor
import ru.hollowhorizon.hollowengine.common.geary.anchor.MaterializationRuntimeState
import ru.hollowhorizon.hollowengine.common.geary.api.geary
import ru.hollowhorizon.hollowengine.common.geary.components.Model
import ru.hollowhorizon.hollowengine.common.geary.components.TransformComponent
import ru.hollowhorizon.hollowengine.fabric.internal.IrisHelper

object RenderManager {
    fun onInitialize() {
        HollowModelManager.initialize()
        KoolManager
    }

    @SubscribeEvent
    fun onPrepareClusteredLighting(event: RenderLevelStageEvent) {
        if (event.stage != RenderStage.AFTER_SKY) return
        ClusteredLightingManager.prepareFrame(event)
    }

    @SubscribeEvent
    fun onRenderInstanced(event: RenderLevelStageEvent) {
        when (event.stage) {
            RenderStage.AFTER_ENTITIES -> InstanceBatchManager.flush()
            RenderStage.AFTER_LEVEL -> InstanceBatchManager.clear()
            else -> {}
        }
    }

    @SubscribeEvent
    fun onRenderAnchoredModels(event: RenderLevelStageEvent) {
        if (event.stage != RenderStage.AFTER_ENTITIES) return

        val minecraft = Minecraft.getInstance()
        minecraft.level ?: return
        val bufferSource = minecraft.renderBuffers().bufferSource()

        renderAnchoredModels(
            partialTick = event.partialTick,
            poseStack = event.poseStack,
            bufferSource = bufferSource,
            cameraPosition = event.camera.position,
            frustum = event.frustum,
            packedLight = -1,
            allowInstancing = shouldAllowInstancingInCurrentPass(),
        )

        bufferSource.endBatch()
    }

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
        renderAnchoredShadowCasters(
            partialTick = partialTick,
            poseStack = modelView,
            bufferSource = bufferSource,
            cameraPosition = cameraPosition,
            frustum = frustum,
            packedLight = LightTexture.FULL_BRIGHT,
            allowInstancing = false,
        )
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
        renderAnchoredShadowCasters(
            partialTick = partialTick,
            poseStack = modelView,
            bufferSource = bufferSource,
            cameraPosition = Vec3(cameraX, cameraY, cameraZ),
            frustum = frustum,
            packedLight = LightTexture.FULL_BRIGHT,
            allowInstancing = false,
        )
    }

    @SubscribeEvent
    fun onRenderParticles(event: RenderLevelStageEvent) {
        if (event.stage != RenderStage.AFTER_PARTICLES) return

        val level = Minecraft.getInstance().level ?: return
        val camera = event.camera

        val system = level.system
        if (system.isEmpty()) return

        system.update()

        if (!system.hasAnythingToRender()) return

        val cameraUuid = camera.entity.uuid
        val cameraRotMc = camera.rotation()
        val position = camera.position

        val isFirstPerson = Minecraft.getInstance().options.cameraType == CameraType.FIRST_PERSON
        system.render(
            event.poseStack,
            Vec3f(position.x.toFloat(), position.y.toFloat(), position.z.toFloat()),
            QuatF(cameraRotMc.x(), cameraRotMc.y(), cameraRotMc.z(), cameraRotMc.w()),
            ParticleVertexConsumerProvider,
            cameraUuid,
            isFirstPerson
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
            if (!dispatcher.shouldRender(entity, frustum, cameraPosition.x, cameraPosition.y, cameraPosition.z)) return@forEach
            renderer.invokeRenderEntity(entity, cameraPosition.x, cameraPosition.y, cameraPosition.z, partialTick, modelView, bufferSource)
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
            renderer.invokeRenderEntity(vehicle, cameraPosition.x, cameraPosition.y, cameraPosition.z, partialTick, modelView, bufferSource)
        }
        player.passengers.forEach { passenger ->
            renderer.invokeRenderEntity(passenger, cameraPosition.x, cameraPosition.y, cameraPosition.z, partialTick, modelView, bufferSource)
        }
        renderer.invokeRenderEntity(player, cameraPosition.x, cameraPosition.y, cameraPosition.z, partialTick, modelView, bufferSource)
    }

    private fun renderAnchoredModels(
        partialTick: Float,
        poseStack: PoseStack,
        bufferSource: MultiBufferSource,
        cameraPosition: Vec3,
        frustum: Frustum?,
        packedLight: Int,
        allowInstancing: Boolean,
    ) {
        val minecraft = Minecraft.getInstance()
        val level = minecraft.level ?: return
        val materialization = MaterializationRuntimeState.service(level)

        materialization.records.forEach { record ->
            if ((record.anchor as? EntityAnchor)?.primary == true) return@forEach

            with(level.geary) {
                val entity = record.runtimeId.toGeary()
                val model = entity.get<Model>() ?: return@with
                val transform = entity.get<TransformComponent>() ?: TransformComponent()
                val resolved = resolveAnchoredTransform(level, record.anchor, transform, partialTick)
                    ?: return@with

                val bounds = buildAnchoredRenderBounds(model, resolved.transform, model.scale)
                if (frustum != null && !frustum.isVisible(bounds)) return@with

                poseStack.pushPose()
                poseStack.translate(
                    resolved.transform.translation.x - cameraPosition.x,
                    resolved.transform.translation.y - cameraPosition.y,
                    resolved.transform.translation.z - cameraPosition.z,
                )
                poseStack.mulPose(
                    Quaternionf(
                        resolved.transform.rotation.x,
                        resolved.transform.rotation.y,
                        resolved.transform.rotation.z,
                        resolved.transform.rotation.w,
                    )
                )
                poseStack.scale(
                    model.scale * resolved.transform.scale.x,
                    model.scale * resolved.transform.scale.y,
                    model.scale * resolved.transform.scale.z,
                )
                model.attachment.pipeline.render(
                    RenderContext(
                        poseStack,
                        bufferSource,
                        if (packedLight >= 0) packedLight else resolved.light,
                        OverlayTexture.NO_OVERLAY,
                        allowInstancing = allowInstancing,
                    )
                )
                poseStack.popPose()
            }
        }
    }

    private fun renderAnchoredShadowCasters(
        partialTick: Float,
        poseStack: PoseStack,
        bufferSource: MultiBufferSource,
        cameraPosition: Vec3,
        frustum: Frustum?,
        packedLight: Int,
        allowInstancing: Boolean,
    ) {
        renderAnchoredModels(
            partialTick = partialTick,
            poseStack = poseStack,
            bufferSource = bufferSource,
            cameraPosition = cameraPosition,
            frustum = frustum,
            packedLight = packedLight,
            allowInstancing = allowInstancing,
        )
    }

    private fun shouldAllowInstancingInCurrentPass(): Boolean =
        !IrisHelper.isShadowRendering() && !ClusteredLightingManager.isLocalShadowPassActive()
}
