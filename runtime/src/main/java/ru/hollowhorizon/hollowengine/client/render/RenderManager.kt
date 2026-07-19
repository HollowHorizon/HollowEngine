package ru.hollowhorizon.hollowengine.client.render

import com.mojang.blaze3d.vertex.PoseStack
import de.fabmax.kool.math.QuatF
import de.fabmax.kool.math.Vec3f
import net.minecraft.client.CameraType
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.culling.Frustum
import net.minecraft.client.renderer.entity.LivingEntityRenderer
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.util.Mth
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3
import org.joml.Quaternionf
import ru.hollowhorizon.hollowengine.api.system
import ru.hollowhorizon.hollowengine.client.kool.KoolManager
import ru.hollowhorizon.hollowengine.client.models.internal.animator.AnimatorEvaluationContext
import ru.hollowhorizon.hollowengine.client.models.internal.animator.AnimatorRuntimeKey
import ru.hollowhorizon.hollowengine.client.models.internal.manager.HollowModelManager
import ru.hollowhorizon.hollowengine.client.models.internal.rendering.InstanceBatchManager
import ru.hollowhorizon.hollowengine.client.models.internal.rendering.RenderContext
import ru.hollowhorizon.hollowengine.client.particles.ParticleVertexConsumerProvider
import ru.hollowhorizon.hollowengine.client.render.lighting.ClusteredLightingManager
import ru.hollowhorizon.hollowengine.common.events.ClientOnly
import ru.hollowhorizon.hollowengine.common.events.RequireMod
import ru.hollowhorizon.hollowengine.common.events.SubscribeEvent
import ru.hollowhorizon.hollowengine.common.events.client.render.RenderLevelStageEvent
import ru.hollowhorizon.hollowengine.common.events.client.render.RenderStage
import ru.hollowhorizon.hollowengine.common.geary.binding.NodeRuntimeState
import ru.hollowhorizon.hollowengine.fabric.internal.IrisHelper
import kotlin.math.abs

@ClientOnly
object RenderManager {
    fun onInitialize() {
        HollowModelManager.initialize()
        KoolManager
    }

    @SubscribeEvent
    @RequireMod("iris")
    fun onPrepareClusteredLighting(event: RenderLevelStageEvent) {
        if (event.stage != RenderStage.AFTER_SKY) return
        ClusteredLightingManager.prepareFrame(event)
    }

    @SubscribeEvent
    @RequireMod("iris")
    fun onDispatchDeferredLightCulling(event: RenderLevelStageEvent) {
        ClusteredLightingManager.dispatchDeferredCulling(event)
    }

    @SubscribeEvent
    fun onRenderInstanced(event: RenderLevelStageEvent) {
        when (event.stage) {
            RenderStage.AFTER_ENTITIES -> {
                InstanceBatchManager.flush()
                InstanceBatchManager.clear()
            }
            RenderStage.AFTER_LEVEL -> {
                InstanceBatchManager.flush()
                InstanceBatchManager.clear()
            }
            else -> {}
        }
    }

    @SubscribeEvent
    fun onRenderNodeModels(event: RenderLevelStageEvent) {
        if (event.stage != RenderStage.AFTER_ENTITIES) return

        val minecraft = Minecraft.getInstance()
        minecraft.level ?: return
        val bufferSource = minecraft.renderBuffers().bufferSource()

        val renderStats = renderNodeModels(
            partialTick = event.partialTick,
            poseStack = event.poseStack,
            bufferSource = bufferSource,
            cameraPosition = event.camera.position,
            frustum = event.frustum,
            packedLight = -1,
            allowInstancing = shouldAllowInstancingInCurrentPass(),
        )
        flushNodeBatches(bufferSource, renderStats)
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



    internal fun renderNodeModels(
        partialTick: Float,
        poseStack: PoseStack,
        bufferSource: MultiBufferSource,
        cameraPosition: Vec3,
        frustum: Frustum?,
        packedLight: Int,
        allowInstancing: Boolean,
    ): NodeRenderStats {
        val minecraft = Minecraft.getInstance()
        val level = minecraft.level ?: return NodeRenderStats.EMPTY
        val materialization = NodeRuntimeState.service(level)
        var renderedAny = false
        val openedBatchedRenderTypes = LinkedHashSet<net.minecraft.client.renderer.RenderType>()

        materialization.forEachModelNodeRecord { record, node ->
            if (record.hostEntity === minecraft.player && minecraft.options.cameraType == CameraType.FIRST_PERSON) {
                return@forEachModelNodeRecord
            }

            val model = node.model
            val transform = node.transform
            val resolved = resolveNodeTransform(level, record.hostEntityUuid, transform, partialTick)
                ?: return@forEachModelNodeRecord

            val bounds = buildNodeRenderBounds(model, resolved.transform)
            if (frustum != null && !frustum.isVisible(bounds)) return@forEachModelNodeRecord

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
                resolved.transform.scale.x,
                resolved.transform.scale.y,
                resolved.transform.scale.z,
            )
            model.attachment.entity = record.hostEntity as? LivingEntity
            model.attachment.configureAnimator(
                animator = node.animator,
                key = AnimatorRuntimeKey(record.snapshotId, node.nodeId, model.model),
                context = animatorContext(record.hostEntity, partialTick),
            )
            model.attachment.pipeline.render(
                RenderContext(
                    poseStack,
                    bufferSource,
                    if (packedLight >= 0) packedLight else resolved.light,
                    (record.hostEntity as? LivingEntity)?.let { LivingEntityRenderer.getOverlayCoords(it, 0f) }
                        ?: OverlayTexture.NO_OVERLAY,
                    allowInstancing = allowInstancing,
                    openedBatchedRenderTypes = openedBatchedRenderTypes,
                )
            )
            poseStack.popPose()
            renderedAny = true
        }
        return NodeRenderStats(renderedAny, openedBatchedRenderTypes)
    }

    private fun animatorContext(entity: Entity?, partialTick: Float): AnimatorEvaluationContext {
        val baseLevelTime = Minecraft.getInstance().level?.gameTime?.toFloat() ?: 0f
        val levelTime = baseLevelTime + partialTick
        val time = (entity?.tickCount?.toFloat() ?: baseLevelTime) + partialTick
        val values = linkedMapOf(
            "partial_tick" to partialTick,
            "game_time" to levelTime,
            "life_time" to time,
            "age" to time,
        )
        if (entity != null) {
            val velocity = entity.deltaMovement
            val movementYaw = when (entity) {
                is LivingEntity -> Mth.rotLerp(partialTick, entity.yBodyRotO, entity.yBodyRot)
                else -> Mth.rotLerp(partialTick, entity.yRotO, entity.yRot)
            }
            val localForwardSpeed = localForwardSpeed(velocity, movementYaw)
            val localSideSpeed = localSideSpeed(velocity, movementYaw)
            val rawHorizontalSpeed = velocity.horizontalDistance().toFloat() * 20f
            val signedLocomotionSpeed = signedLocomotionSpeed(rawHorizontalSpeed, localForwardSpeed)
            values += mapOf(
                "entity_id" to entity.id.toFloat(),
                "is_alive" to if (entity.isAlive) 1f else 0f,
                "is_on_ground" to if (entity.onGround()) 1f else 0f,
                "velocity_x" to velocity.x.toFloat(),
                "velocity_y" to velocity.y.toFloat(),
                "velocity_z" to velocity.z.toFloat(),
                "horizontal_speed" to rawHorizontalSpeed,
                "local_forward_speed" to localForwardSpeed,
                "local_side_speed" to localSideSpeed,
                "signed_horizontal_speed" to signedLocomotionSpeed,
                "movement_animation_speed" to signedLocomotionSpeed * MOVEMENT_ANIMATION_SPEED_SCALE,
            )
        }
        if (entity is LivingEntity) {
            val bodyYaw = Mth.rotLerp(partialTick, entity.yBodyRotO, entity.yBodyRot)
            val headYaw = Mth.rotLerp(partialTick, entity.yHeadRotO, entity.yHeadRot)
            val headPitch = Mth.lerp(partialTick, entity.xRotO, entity.xRot)
            val headBodyYaw = Mth.wrapDegrees(headYaw - bodyYaw)
            values += mapOf(
                "hurt_time" to entity.hurtTime.toFloat(),
                "head_y_rotation" to headYaw,
                "head_x_rotation" to headPitch,
                "body_y_rotation" to bodyYaw,
                "head_body_y_delta" to headBodyYaw,
                "walk_animation_speed" to entity.walkAnimation.speed(partialTick),
                "walk_animation_position" to entity.walkAnimation.position(partialTick),
                "is_sprinting" to if (entity.isSprinting) 1f else 0f,
                "is_sneaking" to if (entity.isShiftKeyDown) 1f else 0f,
            )
        }
        return AnimatorEvaluationContext(deltaTime = 0f, time = time, values = values)
    }



    internal fun flushNodeBatches(bufferSource: MultiBufferSource, renderStats: NodeRenderStats) {
        if (!renderStats.renderedAny || renderStats.openedBatchedRenderTypes.isEmpty()) return
        val flushable = bufferSource as? MultiBufferSource.BufferSource ?: return
        renderStats.openedBatchedRenderTypes.forEach(flushable::endBatch)
    }

    private fun shouldAllowInstancingInCurrentPass(): Boolean =
        !IrisHelper.isShadowRendering() && !ClusteredLightingManager.isLocalShadowPassActive()

    private fun localForwardSpeed(velocity: Vec3, yaw: Float): Float {
        val yawRad = yaw * Mth.DEG_TO_RAD
        val forwardX = -Mth.sin(yawRad)
        val forwardZ = Mth.cos(yawRad)
        return velocity.x.toFloat() * forwardX + velocity.z.toFloat() * forwardZ
    }

    private fun localSideSpeed(velocity: Vec3, yaw: Float): Float {
        val yawRad = yaw * Mth.DEG_TO_RAD
        val rightX = Mth.cos(yawRad)
        val rightZ = Mth.sin(yawRad)
        return velocity.x.toFloat() * rightX + velocity.z.toFloat() * rightZ
    }

    private fun signedLocomotionSpeed(horizontalSpeed: Float, localForwardSpeed: Float): Float =
        if (abs(localForwardSpeed) > LOCAL_MOVEMENT_EPSILON && localForwardSpeed < 0f) {
            -horizontalSpeed
        } else {
            horizontalSpeed
        }

    internal data class NodeRenderStats(
        val renderedAny: Boolean,
        val openedBatchedRenderTypes: Set<net.minecraft.client.renderer.RenderType>,
    ) {
        companion object {
            val EMPTY = NodeRenderStats(renderedAny = false, openedBatchedRenderTypes = emptySet())
        }
    }
}

private const val LOCAL_MOVEMENT_EPSILON = 0.001f
private const val MOVEMENT_ANIMATION_SPEED_SCALE = 1f
