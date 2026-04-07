package ru.hollowhorizon.hollowengine.client.render

import de.fabmax.kool.math.QuatF
import de.fabmax.kool.math.Vec3f
import net.minecraft.client.CameraType
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.LevelRenderer
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.core.BlockPos
import net.minecraft.util.Mth
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.joml.Quaternionf
import ru.hollowhorizon.hollowengine.api.system
import ru.hollowhorizon.hollowengine.client.kool.KoolManager
import ru.hollowhorizon.hollowengine.client.models.internal.rendering.RenderContext
import ru.hollowhorizon.hollowengine.client.models.internal.manager.HollowModelManager
import ru.hollowhorizon.hollowengine.client.models.internal.rendering.InstanceBatchManager
import ru.hollowhorizon.hollowengine.client.models.internal.v2.calculateBounds
import ru.hollowhorizon.hollowengine.client.particles.ParticleVertexConsumerProvider
import ru.hollowhorizon.hollowengine.common.events.SubscribeEvent
import ru.hollowhorizon.hollowengine.common.events.client.render.RenderLevelStageEvent
import ru.hollowhorizon.hollowengine.common.events.client.render.RenderStage
import ru.hollowhorizon.hollowengine.common.geary.anchor.EntityAnchor
import ru.hollowhorizon.hollowengine.common.geary.anchor.MaterializationRuntimeState
import ru.hollowhorizon.hollowengine.common.geary.anchor.WorldAnchor
import ru.hollowhorizon.hollowengine.common.geary.api.geary
import ru.hollowhorizon.hollowengine.common.geary.components.Model
import ru.hollowhorizon.hollowengine.common.geary.components.TransformComponent
import ru.hollowhorizon.hollowengine.common.geary.tracking.MCEntity
import kotlin.math.cos
import kotlin.math.sin

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
            when (val anchor = record.anchor) {
                is EntityAnchor -> if (anchor.primary) return@forEach
                is WorldAnchor -> Unit
            }

            with(level.geary) {
                val entity = record.runtimeId.toGeary()
                val model = entity.get<Model>() ?: return@with
                val transform = entity.get<TransformComponent>() ?: TransformComponent()
                val resolved = resolveTransform(level, anchor = record.anchor, transform = transform, partialTick = event.partialTick)
                    ?: return@with

                val scale = model.scale * resolved.scale
                val bounds = buildRenderBounds(model, resolved.position, scale)
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

    private data class ResolvedTransform(
        val position: Vec3,
        val yaw: Float,
        val pitch: Float,
        val scale: Float,
        val light: Int,
    )

    private fun resolveTransform(
        level: net.minecraft.world.level.Level,
        anchor: Any,
        transform: TransformComponent,
        partialTick: Float,
    ): ResolvedTransform? {
        val position = when (anchor) {
            is WorldAnchor -> Vec3(transform.x.toDouble(), transform.y.toDouble(), transform.z.toDouble())
            is EntityAnchor -> {
                val host = findHostEntity(level, anchor.hostUuid) ?: return null
                val hostYaw = if (host is LivingEntity) {
                    Mth.rotLerp(partialTick, host.yBodyRotO, host.yBodyRot)
                } else {
                    Mth.rotLerp(partialTick, host.yRotO, host.yRot)
                }
                val hostPos = Vec3(
                    Mth.lerp(partialTick.toDouble(), host.xOld, host.x),
                    Mth.lerp(partialTick.toDouble(), host.yOld, host.y),
                    Mth.lerp(partialTick.toDouble(), host.zOld, host.z),
                )
                val rotatedOffset = rotateAroundY(
                    Vec3(transform.x.toDouble(), transform.y.toDouble(), transform.z.toDouble()),
                    -hostYaw * Mth.DEG_TO_RAD,
                )
                hostPos.add(rotatedOffset)
            }
            else -> return null
        }

        val yaw = when (anchor) {
            is EntityAnchor -> {
                val host = findHostEntity(level, anchor.hostUuid)
                val hostYaw = when (host) {
                    is LivingEntity -> Mth.rotLerp(partialTick, host.yBodyRotO, host.yBodyRot)
                    null -> 0f
                    else -> Mth.rotLerp(partialTick, host.yRotO, host.yRot)
                }
                hostYaw + transform.yaw
            }
            else -> transform.yaw
        }
        val light = LevelRenderer.getLightColor(level, BlockPos.containing(position))
        return ResolvedTransform(position, yaw, transform.pitch, transform.scale, light)
    }

    private fun findHostEntity(level: net.minecraft.world.level.Level, hostUuid: java.util.UUID): MCEntity? {
        val runtimeId = MaterializationRuntimeState.service(level).runtimeIdOf(hostUuid) ?: return null
        return with(level.geary) { runtimeId.toGeary().get<MCEntity>() }
    }

    private fun rotateAroundY(vector: Vec3, yawRadians: Float): Vec3 {
        val cos = cos(yawRadians)
        val sin = sin(yawRadians)
        return Vec3(
            vector.x * cos - vector.z * sin,
            vector.y,
            vector.x * sin + vector.z * cos,
        )
    }

    private fun buildRenderBounds(model: Model, position: Vec3, scale: Float): AABB {
        val localBounds = model.attachment.calculateBounds()
        if (localBounds == null) {
            return AABB(
                position.x - scale.toDouble(),
                position.y - scale.toDouble(),
                position.z - scale.toDouble(),
                position.x + scale.toDouble(),
                position.y + scale.toDouble(),
                position.z + scale.toDouble(),
            )
        }

        val min = localBounds.first
        val max = localBounds.second
        return AABB(
            position.x + min.x * scale,
            position.y + min.y * scale,
            position.z + min.z * scale,
            position.x + max.x * scale,
            position.y + max.y * scale,
            position.z + max.z * scale,
        )
    }
}
