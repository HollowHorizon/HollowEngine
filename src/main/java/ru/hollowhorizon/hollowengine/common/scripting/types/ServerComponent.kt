package ru.hollowhorizon.hollowengine.common.scripting.types

import net.minecraft.client.renderer.entity.LivingEntityRenderer
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.server.MinecraftServer
import net.minecraft.util.Mth
import net.minecraft.world.entity.LivingEntity
import org.joml.Quaternionf
import ru.hollowhorizon.hollowengine.client.models.internal.rendering.RenderContext
import ru.hollowhorizon.hollowengine.client.models.internal.v2.ModelAttachment
import ru.hollowhorizon.hollowengine.common.components.Component
import ru.hollowhorizon.hollowengine.common.components.events.on
import ru.hollowhorizon.hollowengine.common.events.client.render.RenderEntityEvent

open class ServerComponent(owner: MinecraftServer): Component<MinecraftServer>(owner)
open class LivingEntityComponent(owner: LivingEntity): Component<LivingEntity>(owner) {
    // TODO: Вот эту дичь перенести в context параметры, когда они заработают
    fun ModelAttachment.bindRenderer() {
        on<RenderEntityEvent.Pre>().onlyOwner { it.entity }.listen { event ->
            with(event) {
                poseStack.pushPose()

                var overlay = OverlayTexture.NO_OVERLAY
                if (this.entity is LivingEntity) {
                    poseStack.mulPose(
                        Quaternionf().rotateY(
                            -Mth.rotLerp(
                                partialTicks,
                                entity.yBodyRotO,
                                entity.yBodyRot
                            ) * Mth.DEG_TO_RAD
                        )
                    )
                    overlay = LivingEntityRenderer.getOverlayCoords(entity, 0f)
                }

                pipeline.render(RenderContext(poseStack, buffer, packedLight, overlay))
                poseStack.popPose()

                isCanceled = true
            }
        }
    }
}