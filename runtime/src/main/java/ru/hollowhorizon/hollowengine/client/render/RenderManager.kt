package ru.hollowhorizon.hollowengine.client.render

import de.fabmax.kool.math.QuatF
import de.fabmax.kool.math.Vec3f
import net.minecraft.client.CameraType
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.util.Mth
import org.joml.Quaternionf
import ru.hollowhorizon.hollowengine.api.system
import ru.hollowhorizon.hollowengine.client.kool.KoolManager
import ru.hollowhorizon.hollowengine.client.models.internal.manager.HollowModelManager
import ru.hollowhorizon.hollowengine.client.models.internal.rendering.InstanceBatchManager
import ru.hollowhorizon.hollowengine.client.models.internal.rendering.RenderContext
import ru.hollowhorizon.hollowengine.client.particles.ParticleVertexConsumerProvider
import ru.hollowhorizon.hollowengine.common.events.SubscribeEvent
import ru.hollowhorizon.hollowengine.common.events.client.render.RenderLevelStageEvent
import ru.hollowhorizon.hollowengine.common.events.client.render.RenderStage
import ru.hollowhorizon.hollowengine.common.geary.anchor.EntityAnchor
import ru.hollowhorizon.hollowengine.common.geary.anchor.MaterializationRuntimeState
import ru.hollowhorizon.hollowengine.common.geary.api.geary
import ru.hollowhorizon.hollowengine.common.geary.components.Model
import ru.hollowhorizon.hollowengine.common.geary.components.TransformComponent

object RenderManager {
    fun onInitialize() {
        HollowModelManager.initialize()
        KoolManager
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
        val level = minecraft.level ?: return
        val cameraPosition = event.camera.position
        val bufferSource = minecraft.renderBuffers().bufferSource()
        val materialization = MaterializationRuntimeState.service(level)

        materialization.records.forEach { record ->
            if ((record.anchor as? EntityAnchor)?.primary == true) return@forEach

            with(level.geary) {
                val entity = record.runtimeId.toGeary()
                val model = entity.get<Model>() ?: return@with
                val transform = entity.get<TransformComponent>() ?: TransformComponent()
                val resolved = resolveAnchoredTransform(level, record.anchor, transform, event.partialTick)
                    ?: return@with

                val scale = model.scale * resolved.scale
                val bounds = buildAnchoredRenderBounds(model, resolved.position, scale)
                if (event.frustum != null && !event.frustum.isVisible(bounds)) return@with

                event.poseStack.pushPose()
                event.poseStack.translate(
                    resolved.position.x - cameraPosition.x,
                    resolved.position.y - cameraPosition.y,
                    resolved.position.z - cameraPosition.z,
                )
                event.poseStack.mulPose(Quaternionf().rotateY(resolved.yaw * Mth.DEG_TO_RAD))
                event.poseStack.mulPose(Quaternionf().rotateX(resolved.pitch * Mth.DEG_TO_RAD))
                event.poseStack.scale(scale, scale, scale)
                model.attachment.pipeline.render(
                    RenderContext(
                        event.poseStack,
                        bufferSource,
                        resolved.light,
                        OverlayTexture.NO_OVERLAY,
                        allowInstancing = true,
                    )
                )
                event.poseStack.popPose()
            }
        }

        bufferSource.endBatch()
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
}
