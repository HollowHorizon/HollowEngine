package ru.hollowhorizon.hollowengine.client.render

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.network.chat.Component
import ru.hollowhorizon.hc.client.render.entity.GLTFEntityRenderer
import ru.hollowhorizon.hc.client.utils.get
import ru.hollowhorizon.hc.common.events.SubscribeEvent
import ru.hollowhorizon.hc.common.events.registry.RegisterEntityRenderersEvent
import ru.hollowhorizon.hollowengine.common.entities.NPCEntity
import ru.hollowhorizon.hollowengine.common.npcs.NPCCapability
import ru.hollowhorizon.hollowengine.common.npcs.NpcIcon
import ru.hollowhorizon.hollowengine.common.registry.ModEntities

class NPCRenderer(context: EntityRendererProvider.Context) : GLTFEntityRenderer<NPCEntity>(context) {
    override fun renderNameTag(
        pEntity: NPCEntity,
        pDisplayName: Component,
        pMatrixStack: PoseStack,
        pBuffer: MultiBufferSource,
        pPackedLight: Int,
        partialTick: Float,
    ) {
        super.renderNameTag(pEntity, pDisplayName, pMatrixStack, pBuffer, pPackedLight, partialTick)

        val icon = pEntity[NPCCapability::class].icon

        if (icon == NpcIcon.EMPTY) return

        val dist = entityRenderDispatcher.distanceToSqr(pEntity)
        if (dist <= 4096) {
            val f = pEntity.bbHeight + 0.75f + icon.offsetY

            pMatrixStack.pushPose()
            pMatrixStack.translate(0.0, f.toDouble(), 0.0)
            pMatrixStack.mulPose(entityRenderDispatcher.cameraOrientation())
            pMatrixStack.scale(-0.025f, -0.025f, 0.025f)

            val size = (16f * icon.scale).toInt()
            val pos = size / 2

            GuiGraphics(Minecraft.getInstance(), Minecraft.getInstance().renderBuffers().bufferSource())
                .apply {
                    pose().mulPose(pMatrixStack.last().pose())
                }
                .blit(icon.image, -pos, -pos, 0f, 0f, size, size, size, size)

            pMatrixStack.popPose()
        }
    }
}

@SubscribeEvent
fun registerEntityRenderer(context: RegisterEntityRenderersEvent) {
    context.registerEntity(ModEntities.NPC_ENTITY.get(), ::NPCRenderer)
}