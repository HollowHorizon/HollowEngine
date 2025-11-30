package ru.hollowhorizon.hollowengine.common.components.entity

import net.minecraft.client.renderer.entity.LivingEntityRenderer
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.util.Mth
import net.minecraft.world.entity.LivingEntity
import org.joml.Quaternionf
import ru.hollowhorizon.hollowengine.client.models.internal.manager.HollowModelManager
import ru.hollowhorizon.hollowengine.client.models.internal.rendering.ListRenderPipeline
import ru.hollowhorizon.hollowengine.client.models.internal.rendering.RenderContext
import ru.hollowhorizon.hollowengine.client.models.internal.v2.ClientModel
import ru.hollowhorizon.hollowengine.common.components.Component
import ru.hollowhorizon.hollowengine.common.events.SubscribeEvent
import ru.hollowhorizon.hollowengine.common.events.client.render.RenderEntityEvent
import ru.hollowhorizon.hollowengine.common.utils.rl

class ModelComponent(entity: LivingEntity) : Component<LivingEntity>(entity) {
    val model by lazy {
        ClientModel(HollowModelManager.getOrCreate("hollowengine:models/entity/player_model.gltf".rl).model)
    }

    private val pipeline by lazy {
        ListRenderPipeline().apply { model.collectCommands(this) }
    }

    @SubscribeEvent
    fun onRender(event: RenderEntityEvent.Pre) = with(event) {
        poseStack.pushPose()

        var overlay = OverlayTexture.NO_OVERLAY
        if (entity is LivingEntity) {
            poseStack.mulPose(
                Quaternionf().rotateY(-Mth.rotLerp(partialTicks, entity.yBodyRotO, entity.yBodyRot) * Mth.DEG_TO_RAD)
            )
            overlay = LivingEntityRenderer.getOverlayCoords(entity, 0f)
        }

        pipeline.render(
            RenderContext(poseStack, buffer, packedLight, overlay)
        )
        poseStack.popPose()

        isCanceled = true
    }

}