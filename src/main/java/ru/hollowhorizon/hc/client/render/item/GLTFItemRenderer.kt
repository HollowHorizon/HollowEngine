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

package ru.hollowhorizon.hc.client.render.item


import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.Mth
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.EntityBlock
import org.joml.Quaternionf
import ru.hollowhorizon.hc.client.models.internal.ModelData
import ru.hollowhorizon.hc.client.models.internal.manager.AnimatedEntityCapability
import ru.hollowhorizon.hc.client.models.internal.manager.HollowModelManager
import ru.hollowhorizon.hc.client.models.internal.manager.IAnimated
import ru.hollowhorizon.hc.client.render.entity.HollowEntityRenderer
import ru.hollowhorizon.hc.client.utils.SkinDownloader
import ru.hollowhorizon.hc.client.utils.*
import ru.hollowhorizon.hc.common.utils.get
import ru.hollowhorizon.hc.common.utils.memoize
import ru.hollowhorizon.hc.common.utils.rl


object GLTFItemRenderer : BlockEntityWithoutLevelRenderer(
    Minecraft.getInstance().blockEntityRenderDispatcher, Minecraft.getInstance().entityModels
) {

    override fun renderByItem(
        itemStack: ItemStack,
        transforms: ItemDisplayContext,
        stack: PoseStack,
        buffer: MultiBufferSource,
        packedLight: Int,
        packedOverlay: Int,
    ) {
        val item = itemStack.item
        if (item !is BlockItem) return

        val block = item.block
        if (block !is EntityBlock) return

        val state = block.newBlockEntity(BlockPos.ZERO, block.defaultBlockState())
        if (state !is IAnimated) return


        val capability = state[AnimatedEntityCapability::class]
        val modelPath = capability.model
        if (modelPath == HollowEntityRenderer.NO_MODEL) return

        val model = HollowModelManager.getOrCreate(modelPath.rl)

        stack.pushPose()

        stack.translate(0.5, 0.0, 0.5)

        stack.mulPoseMatrix(capability.transform.matrix)
        stack.last().normal().mul(capability.transform.normalMatrix)
        if(model.model.isBlockBench) stack.mulPose(Quaternionf().rotateY(180f * Mth.DEG_TO_RAD))
        //model.update(capability.controller)

        model.render(
            stack,
            ModelData(null, null, null, null),
            { texture: ResourceLocation ->
                val result = capability.textures[texture.path]?.let {
                    if (it.startsWith("skins/")) SkinDownloader.downloadSkin(it.substring(6))
                    else it.rl
                } ?: texture

                Minecraft.getInstance().textureManager.getTexture(result).id
            }.memoize(), buffer, packedLight, packedOverlay

        )

        stack.popPose()
    }
}