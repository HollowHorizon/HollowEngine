package ru.hollowhorizon.hollowengine.client.render.entity

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.LivingEntity
import net.minecraftforge.client.ForgeHooksClient
import ru.hollowhorizon.hc.client.models.gltf.manager.IAnimated
import ru.hollowhorizon.hc.client.render.entity.GLTFEntityRenderer
import ru.hollowhorizon.hc.client.utils.get
import ru.hollowhorizon.hollowengine.common.npcs.NPCCapability
import ru.hollowhorizon.hollowengine.common.npcs.NpcIcon

class NPCRenderer<T>(context: EntityRendererProvider.Context) :
    GLTFEntityRenderer<T>(context) where T : LivingEntity, T : IAnimated {

    override fun renderNameTag(
        pEntity: T,
        pDisplayName: Component,
        pMatrixStack: PoseStack,
        pBuffer: MultiBufferSource,
        pPackedLight: Int
    ) {
        super.renderNameTag(pEntity, pDisplayName, pMatrixStack, pBuffer, pPackedLight)

        val icon = pEntity[NPCCapability::class].icon

        if (icon == NpcIcon.EMPTY) return

        val dist = entityRenderDispatcher.distanceToSqr(pEntity)
        if (ForgeHooksClient.isNameplateInRenderDistance(pEntity, dist)) {
            val f = pEntity.bbHeight + 0.75f + icon.offsetY

            pMatrixStack.pushPose()
            pMatrixStack.translate(0.0, f.toDouble(), 0.0)
            pMatrixStack.mulPose(entityRenderDispatcher.cameraOrientation())
            pMatrixStack.scale(-0.025f, -0.025f, 0.025f)

            RenderSystem.setShaderTexture(0, icon.image)

            val size = (16f * icon.scale).toInt()
            val pos = size / 2

            Screen.blit(pMatrixStack, -pos, -pos, 0f, 0f, size, size, size, size)

            pMatrixStack.popPose()
        }
    }
}