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

package ru.hollowhorizon.hollowengine.client.render.block


import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider
import net.minecraft.core.Direction
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.Mth
import net.minecraft.world.level.block.HorizontalDirectionalBlock
import net.minecraft.world.level.block.entity.BlockEntity
import org.joml.Quaternionf
import ru.hollowhorizon.hollowengine.client.handlers.TickHandler
import ru.hollowhorizon.hollowengine.client.models.internal.ModelData
import ru.hollowhorizon.hollowengine.client.models.internal.manager.AnimatedEntityCapability
import ru.hollowhorizon.hollowengine.client.models.internal.manager.HollowModelManager
import ru.hollowhorizon.hollowengine.client.models.internal.manager.IAnimated
import ru.hollowhorizon.hollowengine.client.render.entity.HollowEntityRenderer
import ru.hollowhorizon.hollowengine.client.utils.SkinDownloader
import ru.hollowhorizon.hollowengine.client.utils.*
import ru.hollowhorizon.hollowengine.common.utils.get
import ru.hollowhorizon.hollowengine.common.utils.memoize
import ru.hollowhorizon.hollowengine.common.utils.rl


class HollowBlockEntityRenderer<T>(val pContext: BlockEntityRendererProvider.Context) :
    BlockEntityRenderer<T> where T : BlockEntity, T : IAnimated {
    override fun render(
        entity: T,
        partialTick: Float,
        stack: PoseStack,
        pBufferSource: MultiBufferSource,
        pPackedLight: Int,
        pPackedOverlay: Int,
    ) {
        val level = entity.level ?: return
        if (entity.isRemoved) return
        val capability = entity[AnimatedEntityCapability::class]
        val modelPath = capability.model
        if (modelPath == HollowEntityRenderer.NO_MODEL) return

        val model = HollowModelManager.getOrCreate(modelPath.rl)

        stack.pushPose()

        stack.translate(0.5, 0.0, 0.5)
        stack.mulPoseMatrix(capability.transform.matrix)
        stack.last().normal().mul(capability.transform.normalMatrix)
        if(model.model.isBlockBench) stack.mulPose(Quaternionf().rotateY(180f * Mth.DEG_TO_RAD))

        when (level.getBlockState(entity.blockPos).getOptionalValue(HorizontalDirectionalBlock.FACING)
            .orElseGet { Direction.NORTH }) {
            Direction.SOUTH -> stack.mulPose(Quaternionf(0.0f, 1.0f, 0.0f, 0.0f))
            Direction.WEST -> stack.mulPose(Quaternionf(0.0f, 0.7071068f, 0.0f, 0.7071068f))
            Direction.EAST -> stack.mulPose(Quaternionf(0.0f, -0.7071068f, 0.0f, 0.7071068f))
            else -> {}
        }

        model.update(
            capability.controller,
            capability.molangContext,
            TickHandler.time / 20f
        )

        model.render(
            stack,
            ModelData(null, null, null, null),
            { texture: ResourceLocation ->
                val result = capability.textures[texture.path]?.let {
                    if (it.startsWith("skins/")) SkinDownloader.downloadSkin(it.substring(6))
                    else it.rl
                } ?: texture

                Minecraft.getInstance().textureManager.getTexture(result).id
            }.memoize(),
            pBufferSource,
            pPackedLight,
            pPackedOverlay
        )

        stack.popPose()
    }
}