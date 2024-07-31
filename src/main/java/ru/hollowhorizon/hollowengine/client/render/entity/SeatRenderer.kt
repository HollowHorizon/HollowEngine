package ru.hollowhorizon.hollowengine.client.render.entity

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.entity.EntityRenderer
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import ru.hollowhorizon.hollowengine.common.entities.SeatEntity

class SeatRenderer(pContext: EntityRendererProvider.Context) : EntityRenderer<SeatEntity>(pContext) {
    @Suppress("WRONG_NULLABILITY_FOR_JAVA_OVERRIDE")
    override fun getTextureLocation(p0: SeatEntity): ResourceLocation? = null

    override fun renderNameTag(
        pEntity: SeatEntity,
        pDisplayName: Component,
        pMatrixStack: PoseStack,
        pBuffer: MultiBufferSource,
        pPackedLight: Int
    ) {}
}