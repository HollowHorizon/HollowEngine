package ru.hollowhorizon.hollowengine.client.render

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.entity.LivingEntityRenderer
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.util.Mth
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import org.joml.Quaternionf
import ru.hollowhorizon.hollowengine.client.handlers.TickHandler
import ru.hollowhorizon.hollowengine.client.models.internal.animator.AnimatorEvaluationContext
import ru.hollowhorizon.hollowengine.client.models.internal.animator.AnimatorRuntimeKey
import ru.hollowhorizon.hollowengine.client.models.internal.animator.entityAnimationVariables
import ru.hollowhorizon.hollowengine.client.models.internal.animator.AnimatorRuntimeRegistry
import ru.hollowhorizon.hollowengine.client.models.internal.manager.HollowModelManager
import ru.hollowhorizon.hollowengine.client.models.internal.rendering.InstanceBatchManager
import ru.hollowhorizon.hollowengine.client.models.internal.rendering.RenderContext
import ru.hollowhorizon.hollowengine.common.events.ClientOnly
import ru.hollowhorizon.hollowengine.common.events.SubscribeEvent
import ru.hollowhorizon.hollowengine.common.events.client.render.RenderEntityEvent
import ru.hollowhorizon.hollowengine.common.events.client.render.RenderLevelStageEvent
import ru.hollowhorizon.hollowengine.common.events.client.render.RenderPlayerEvent
import ru.hollowhorizon.hollowengine.common.events.client.render.RenderStage
import ru.hollowhorizon.hollowengine.common.attachments.binding.NodeRuntimeState
import ru.hollowhorizon.hollowengine.fabric.internal.IrisHelper

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

    /**
     * Advances every model's animation once per frame.
     */
    @SubscribeEvent(10)
    fun onPrepareModelFrames(event: RenderLevelStageEvent) {
        if (event.stage != RenderStage.AFTER_ENTITIES) return

        val level = Minecraft.getInstance().level ?: run {
            AnimatorRuntimeRegistry.clear()
            return
        }
        val partialTick = event.partialTick
        val activeAnimatorKeys = HashSet<AnimatorRuntimeKey>()

        NodeRuntimeState.service(level).forEachModelNodeRecord { record, node ->
            val attachment = node.model.attachment
            val animatorKey = AnimatorRuntimeKey(record.snapshotId, node.nodeId, node.model.model)
            if (node.animator != null) activeAnimatorKeys += animatorKey

            attachment.entity = record.hostEntity as? LivingEntity
            attachment.configureAnimator(
                animator = node.animator,
                key = animatorKey,
                context = entityAnimationVariables(record.hostEntity, partialTick),
            )
            attachment.prepareFrame(if (IrisHelper.isShadowRendering()) 0f else TickHandler.deltaFrameTime)
        }

        AnimatorRuntimeRegistry.retain(activeAnimatorKeys)
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
        val openedBatchedRenderTypes = LinkedHashSet<RenderType>()
        val allowInstancing = isWorldPass && shouldAllowInstancingInCurrentPass()
        var renderedAny = false

        materialization.forEachModelNodeOf(entity) { record, node ->
            val attachment = node.model.attachment
            attachment.entity = entity as? LivingEntity
            attachment.configureAnimator(
                animator = node.animator,
                key = AnimatorRuntimeKey(record.snapshotId, node.nodeId, node.model.model),
                context = entityAnimationVariables(entity, partialTick),
            )
            attachment.prepareFrame(if (IrisHelper.isShadowRendering()) 0f else TickHandler.deltaFrameTime)

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
            attachment.pipeline.render(
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

    private fun shouldAllowInstancingInCurrentPass(): Boolean = true
}
