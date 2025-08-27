/*
 * MIT License
 *
 * Copyright (c) 2024 HollowHorizon
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package ru.hollowhorizon.hollowengine.client.render.entity

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.entity.EntityRenderer
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.client.renderer.texture.TextureManager
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.Mth
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemDisplayContext
import org.joml.Quaternionf
import ru.hollowhorizon.hollowengine.client.models.internal.ModelData
import ru.hollowhorizon.hollowengine.client.models.internal.Node
import ru.hollowhorizon.hollowengine.client.models.internal.manager.AnimatedEntityCapability
import ru.hollowhorizon.hollowengine.client.models.internal.manager.HollowModelManager
import ru.hollowhorizon.hollowengine.client.models.internal.manager.IAnimated
import ru.hollowhorizon.hollowengine.client.utils.SkinDownloader
import ru.hollowhorizon.hollowengine.client.utils.*
import ru.hollowhorizon.hollowengine.common.utils.get
import ru.hollowhorizon.hollowengine.common.utils.memoize
import ru.hollowhorizon.hollowengine.common.utils.rl

open class HollowEntityRenderer<T>(manager: EntityRendererProvider.Context) :
    EntityRenderer<T>(manager) where T : LivingEntity, T : IAnimated {
    private val itemInHandRenderer = manager.itemInHandRenderer

    override fun getTextureLocation(entity: T): ResourceLocation {
        return TextureManager.INTENTIONAL_MISSING_TEXTURE
    }

    @Suppress("DEPRECATION")
    override fun render(
        entity: T,
        yaw: Float,
        partialTick: Float,
        stack: PoseStack,
        source: MultiBufferSource,
        packedLight: Int,
    ) {
        super.render(entity, yaw, partialTick, stack, source, packedLight)

        val capability = entity[AnimatedEntityCapability::class]
        val modelPath = capability.model
        if (modelPath == NO_MODEL) return

        val model = HollowModelManager.getOrCreate(modelPath.rl)

        stack.pushPose()
        stack.mulPoseMatrix(capability.transform.matrix)
        stack.last().normal().mul(capability.transform.normalMatrix)
        if(model.model.isBlockBench) stack.mulPose(Quaternionf().rotateY(180f * Mth.DEG_TO_RAD))


        val lerpBodyRot = Mth.rotLerp(partialTick, entity.yBodyRotO, entity.yBodyRot)
        stack.mulPose(Quaternionf().rotateY(-lerpBodyRot * Mth.DEG_TO_RAD))

        model.visuals = ::drawVisuals
        val controller = capability.controller
        controller.uploadAnimations(model.animations)
        model.update(
            controller, capability.molangContext,
            (entity.tickCount + partialTick) / 20f
        )

        model.render(
            stack,
            ModelData(entity.offhandItem, entity.mainHandItem, itemInHandRenderer, entity),
            { texture: ResourceLocation ->
                val result = capability.textures[texture.path]?.let {
                    if (it.startsWith("skins/")) SkinDownloader.downloadSkin(it.substring(6))
                    else it.rl
                } ?: texture

                Minecraft.getInstance().textureManager.getTexture(result).id
            }.memoize(),
            source,
            packedLight,
            OverlayTexture.pack(0, if (entity.hurtTime > 0 || !entity.isAlive) 3 else 10)
        )

        stack.popPose()
    }

    protected open fun drawVisuals(
        entity: LivingEntity,
        stack: PoseStack,
        node: Node,
        source: MultiBufferSource,
        light: Int,
    ) {
        if ((node.name?.contains("left", ignoreCase = true) == true || node.name?.contains(
                "right",
                ignoreCase = true
            ) == true) &&
            node.name.contains("hand", ignoreCase = true) &&
            node.name.contains("item", ignoreCase = true)
        ) {
            val isLeft = node.name.contains("left", ignoreCase = true)
            val item =
                (if (isLeft) entity.getItemInHand(InteractionHand.OFF_HAND) else entity.getItemInHand(InteractionHand.MAIN_HAND))
                    ?: return

            stack.pushPose()
            stack.mulPose(Quaternionf().rotateX(-90 * Mth.DEG_TO_RAD))

            itemInHandRenderer.renderItem(
                entity,
                item,
                if (isLeft) ItemDisplayContext.THIRD_PERSON_LEFT_HAND else ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,
                isLeft,
                stack,
                source,
                light
            )

            stack.popPose()
        }
    }

    companion object {
        const val NO_MODEL = "%NO_MODEL%"
        const val MOVEMENT_FACTOR = (1 / 256f)
    }
}
