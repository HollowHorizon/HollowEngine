package ru.hollowhorizon.hollowengine.client.render

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
//? if >=1.20.1 {
import net.minecraft.client.gui.GuiGraphics
//?} else {
//?}
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.culling.Frustum
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.network.chat.Component
import ru.hollowhorizon.hc.client.render.entity.HollowEntityRenderer
import ru.hollowhorizon.hc.common.utils.get
import ru.hollowhorizon.hc.common.events.SubscribeEvent
import ru.hollowhorizon.hc.common.events.registry.RegisterEntityRenderersEvent
import ru.hollowhorizon.hollowengine.client.gui.dialog.DialogGui
import ru.hollowhorizon.hollowengine.common.entities.NpcEntity
import ru.hollowhorizon.hollowengine.common.npcs.NPCCapability
import ru.hollowhorizon.hollowengine.common.npcs.NpcIcon
import ru.hollowhorizon.hollowengine.common.registry.ModEntities

class NPCRenderer(context: EntityRendererProvider.Context) : HollowEntityRenderer<NpcEntity>(context) {

    override fun renderNameTag(
        pEntity: NpcEntity,
        pDisplayName: Component,
        pMatrixStack: PoseStack,
        pBuffer: MultiBufferSource,
        pPackedLight: Int,
        //? if >=1.21 {
        /*partialTick: Float,
        *///?}
    ) {
        //? if >=1.21 {
        /*super.renderNameTag(pEntity, pDisplayName, pMatrixStack, pBuffer, pPackedLight, partialTick)
        *///?} else {
        super.renderNameTag(pEntity, pDisplayName, pMatrixStack, pBuffer, pPackedLight)
        //?}

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

            //? if >=1.20.1 {
            GuiGraphics(Minecraft.getInstance(), Minecraft.getInstance().renderBuffers().bufferSource())
                .apply {
                    //? if >=1.21 {
                    /*pose().mulPose(pMatrixStack.last().pose())
                    *///?} else {
                    pose().mulPoseMatrix(pMatrixStack.last().pose())
                    //?}
                }
                .blit(icon.image, -pos, -pos, 0f, 0f, size, size, size, size)
            //?} else {
            /*RenderSystem.setShaderTexture(0, icon.image)
            net.minecraft.client.gui.screens.Screen
                .blit(pMatrixStack, -pos, -pos, 0f, 0f, size, size, size, size)
            *///?}

            pMatrixStack.popPose()
        }
    }

    override fun shouldRender(
        livingEntity: NpcEntity,
        camera: Frustum,
        camX: Double,
        camY: Double,
        camZ: Double
    ): Boolean {
        val screen = (Minecraft.getInstance().screen as? DialogGui)?.entities ?: emptyList()
        return livingEntity !in screen && super.shouldRender(livingEntity, camera, camX, camY, camZ)
    }
}

@SubscribeEvent
fun registerEntityRenderer(context: RegisterEntityRenderersEvent) {
    context.registerEntity(ModEntities.NPC_ENTITY, ::NPCRenderer)
}