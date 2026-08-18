package ru.hollowhorizon.hollowengine.client.render

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
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
import ru.hollowhorizon.hollowengine.client.handlers.TickHandler
import ru.hollowhorizon.hollowengine.client.models.internal.animator.AnimatorEvaluationContext
import ru.hollowhorizon.hollowengine.client.models.internal.animator.AnimatorRuntimeKey
import ru.hollowhorizon.hollowengine.client.models.internal.animator.AnimatorRuntimeRegistry
import ru.hollowhorizon.hollowengine.client.models.internal.manager.HollowModelManager
import ru.hollowhorizon.hollowengine.client.models.internal.rendering.InstanceBatchManager
import ru.hollowhorizon.hollowengine.client.models.internal.rendering.RenderContext
import ru.hollowhorizon.hollowengine.client.particles.ParticleVertexConsumerProvider
import ru.hollowhorizon.hollowengine.common.events.ClientOnly
import ru.hollowhorizon.hollowengine.common.events.SubscribeEvent
import ru.hollowhorizon.hollowengine.common.events.client.render.RenderEntityEvent
import ru.hollowhorizon.hollowengine.common.events.client.render.RenderLevelStageEvent
import ru.hollowhorizon.hollowengine.common.events.client.render.RenderPlayerEvent
import ru.hollowhorizon.hollowengine.common.events.client.render.RenderStage
import ru.hollowhorizon.hollowengine.common.geary.binding.NodeRuntimeState
import ru.hollowhorizon.hollowengine.common.utils.math.QuatF
import ru.hollowhorizon.hollowengine.common.utils.math.Vec3f
import ru.hollowhorizon.hollowengine.fabric.internal.IrisHelper
import kotlin.math.abs

@ClientOnly
object RenderManager {
    fun onInitialize() {
        HollowModelManager.initialize()
    }

    private var isWorldPass = false

    @SubscribeEvent
    fun onTrackWorldPass(event: RenderLevelStageEvent) {
        isWorldPass = event.stage != RenderStage.AFTER_LEVEL
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

    @SubscribeEvent(10)
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
        val level = minecraft.level ?: run {
            AnimatorRuntimeRegistry.clear()
            return NodeRenderStats.EMPTY
        }
        val materialization = NodeRuntimeState.service(level)
        var renderedAny = false
        val openedBatchedRenderTypes = LinkedHashSet<net.minecraft.client.renderer.RenderType>()
        val activeAnimatorKeys = HashSet<AnimatorRuntimeKey>()

        materialization.forEachModelNodeRecord { record, node ->
            val model = node.model
            val animatorKey = AnimatorRuntimeKey(record.snapshotId, node.nodeId, model.model)
            if (node.animator != null) activeAnimatorKeys += animatorKey
            model.attachment.entity = record.hostEntity as? LivingEntity
            model.attachment.configureAnimator(
                animator = node.animator,
                key = animatorKey,
                context = animatorContext(record.hostEntity, partialTick),
            )
            model.attachment.prepareFrame(
                if (IrisHelper.isShadowRendering()) 0f else TickHandler.deltaFrameTime,
            )

            if (record.hostEntityUuid != null) return@forEachModelNodeRecord

            if (record.hostEntity === minecraft.player && minecraft.options.cameraType == CameraType.FIRST_PERSON) {
                return@forEachModelNodeRecord
            }

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
        AnimatorRuntimeRegistry.retain(activeAnimatorKeys)
        return NodeRenderStats(renderedAny, openedBatchedRenderTypes)
    }

    @SubscribeEvent
    fun onRenderEntityNodes(event: RenderEntityEvent.Pre) {
        renderHostedNodes(event.entity, event.partialTicks, event.poseStack, event.buffer, event.packedLight)
    }

    @SubscribeEvent
    fun onRenderPlayerNodes(event: RenderPlayerEvent) {
        renderHostedNodes(event.player, event.partialTicks, event.poseStack, event.buffer, event.packedLight)
    }

    private fun renderHostedNodes(
        entity: Entity,
        partialTick: Float,
        poseStack: PoseStack,
        bufferSource: MultiBufferSource,
        packedLight: Int,
    ) {
        val level = Minecraft.getInstance().level ?: return
        val materialization = NodeRuntimeState.service(level)
        val openedBatchedRenderTypes = LinkedHashSet<net.minecraft.client.renderer.RenderType>()
        val allowInstancing = isWorldPass && shouldAllowInstancingInCurrentPass()
        var renderedAny = false

        materialization.forEachModelNodeRecord { record, node ->
            if (record.hostEntity !== entity) return@forEachModelNodeRecord
            val hostYaw = when (entity) {
                is LivingEntity -> Mth.rotLerp(partialTick, entity.yBodyRotO, entity.yBodyRot)
                else -> Mth.rotLerp(partialTick, entity.yRotO, entity.yRot)
            }
            val local = node.transform.transform
            poseStack.pushPose()
            poseStack.mulPose(Axis.YP.rotationDegrees(180f - hostYaw))
            poseStack.translate(
                local.translation.x.toDouble(),
                local.translation.y.toDouble(),
                local.translation.z.toDouble(),
            )
            poseStack.mulPose(Quaternionf(local.rotation.x, local.rotation.y, local.rotation.z, local.rotation.w))
            poseStack.scale(local.scale.x, local.scale.y, local.scale.z)
            node.model.attachment.pipeline.render(
                RenderContext(
                    poseStack,
                    bufferSource,
                    packedLight,
                    (entity as? LivingEntity)?.let { LivingEntityRenderer.getOverlayCoords(it, 0f) }
                        ?: OverlayTexture.NO_OVERLAY,
                    allowInstancing = allowInstancing,
                    openedBatchedRenderTypes = openedBatchedRenderTypes,
                )
            )
            poseStack.popPose()
            renderedAny = true
        }

        if (renderedAny && !isWorldPass) {
            (bufferSource as? MultiBufferSource.BufferSource)
                ?.let { flushable -> openedBatchedRenderTypes.forEach(flushable::endBatch) }
        }
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
                "fall_distance" to entity.fallDistance,
                "is_in_water" to if (entity.isInWater) 1f else 0f,
                "is_under_water" to if (entity.isUnderWater) 1f else 0f,
                "is_in_lava" to if (entity.isInLava) 1f else 0f,
                "is_on_fire" to if (entity.isOnFire) 1f else 0f,
                "remaining_fire_ticks" to entity.remainingFireTicks.toFloat(),
                "is_crouching" to if (entity.isCrouching) 1f else 0f,
                "is_swimming" to if (entity.isSwimming) 1f else 0f,
                "is_visually_swimming" to if (entity.isVisuallySwimming) 1f else 0f,
                "is_visually_crawling" to if (entity.isVisuallyCrawling) 1f else 0f,
                "is_invisible" to if (entity.isInvisible) 1f else 0f,
                "is_glowing" to if (entity.isCurrentlyGlowing) 1f else 0f,
                "is_passenger" to if (entity.isPassenger) 1f else 0f,
                "is_vehicle" to if (entity.isVehicle) 1f else 0f,
                "is_no_gravity" to if (entity.isNoGravity) 1f else 0f,
                "is_in_wall" to if (entity.isInWall) 1f else 0f,
                "is_shift_key_down" to if (entity.isShiftKeyDown) 1f else 0f,
                "eye_height" to entity.eyeHeight,
                "bbox_width" to entity.bbWidth,
                "bbox_height" to entity.bbHeight,
                "horizontal_collision" to if (entity.horizontalCollision) 1f else 0f,
                "vertical_collision" to if (entity.verticalCollision) 1f else 0f,
                "vertical_collision_below" to if (entity.verticalCollisionBelow) 1f else 0f,
                "move_dist" to entity.moveDist,
                "fly_dist" to entity.flyDist,
                "invulnerable_time" to entity.invulnerableTime.toFloat(),
                "ticks_frozen" to entity.ticksFrozen.toFloat(),
                "percent_frozen" to entity.percentFrozen,
                "is_fully_frozen" to if (entity.isFullyFrozen) 1f else 0f,
                "air_supply" to entity.airSupply.toFloat(),
                "max_air_supply" to entity.maxAirSupply.toFloat(),
                "has_custom_name" to if (entity.hasCustomName()) 1f else 0f,
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
                "health" to entity.health,
                "max_health" to entity.maxHealth,
                "health_ratio" to (entity.health / entity.maxHealth.coerceAtLeast(1f)),
                "is_dead_or_dying" to if (entity.isDeadOrDying) 1f else 0f,
                "death_time" to entity.deathTime.toFloat(),
                "death_progress" to (entity.deathTime.toFloat() / LivingEntity.DEATH_DURATION.toFloat()),
                "hurt_duration" to entity.hurtDuration.toFloat(),
                "armor_value" to entity.armorValue.toFloat(),
                "absorption_amount" to entity.absorptionAmount,
                "max_absorption" to entity.maxAbsorption,
                "is_using_item" to if (entity.isUsingItem) 1f else 0f,
                "use_item_remaining_ticks" to entity.useItemRemainingTicks.toFloat(),
                "ticks_using_item" to entity.ticksUsingItem.toFloat(),
                "is_blocking" to if (entity.isBlocking) 1f else 0f,
                "attack_anim" to entity.getAttackAnim(partialTick),
                "swing_time" to entity.swingTime.toFloat(),
                "is_swinging" to if (entity.swinging) 1f else 0f,
                "swim_amount" to entity.getSwimAmount(partialTick),
                "is_fall_flying" to if (entity.isFallFlying) 1f else 0f,
                "fall_flying_ticks" to entity.fallFlyingTicks.toFloat(),
                "is_autospin_attack" to if (entity.isAutoSpinAttack) 1f else 0f,
                "is_climbing" to if (entity.onClimbable()) 1f else 0f,
                "is_sleeping" to if (entity.isSleeping) 1f else 0f,
                "no_jump_delay" to entity.noJumpDelay.toFloat(),
                "jump_boost_power" to entity.jumpBoostPower,
                "speed" to entity.speed,
                "is_sensitive_to_water" to if (entity.isSensitiveToWater) 1f else 0f,
            )
        }
        return AnimatorEvaluationContext(deltaTime = 0f, time = time, values = values)
    }



    internal fun flushNodeBatches(bufferSource: MultiBufferSource, renderStats: NodeRenderStats) {
        if (!renderStats.renderedAny || renderStats.openedBatchedRenderTypes.isEmpty()) return
        val flushable = bufferSource as? MultiBufferSource.BufferSource ?: return
        renderStats.openedBatchedRenderTypes.forEach(flushable::endBatch)
    }

    private fun shouldAllowInstancingInCurrentPass(): Boolean = true

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
