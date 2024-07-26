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

package ru.hollowhorizon.hollowengine.client.screen.overlays

import com.mojang.blaze3d.platform.GlStateManager
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.*
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.GameRenderer
import net.minecraft.client.renderer.RenderType
import net.minecraft.world.phys.AABB
import ru.hollowhorizon.hc.client.handlers.TickHandler
import ru.hollowhorizon.hc.client.utils.mc
import ru.hollowhorizon.hc.client.utils.rl
import ru.hollowhorizon.hollowengine.client.shaders.ModShaders
import kotlin.math.min


/*class HollowRenderTypes(
    pName: String,
    pFormat: VertexFormat,
    pMode: VertexFormat.Mode,
    pBufferSize: Int,
    pAffectsCrumbling: Boolean,
    pSortOnUpload: Boolean,
    pSetupState: Runnable,
    pClearState: Runnable,
) : RenderType(pName, pFormat, pMode, pBufferSize, pAffectsCrumbling, pSortOnUpload, pSetupState, pClearState) {
    companion object {
        fun overlayQuads(): RenderType {
            return create(
                "hc_overlay_quads",
                DefaultVertexFormat.POSITION_COLOR_TEX,
                VertexFormat.Mode.QUADS,
                256,
                false,
                false,
                CompositeState.builder()
                    .setShaderState(ShaderStateShard { ModShaders.BARRIER })
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setCullState(NO_CULL)
                    .setTextureState(
                        MultiTextureStateShard.builder()
                            .add("minecraft:textures/block/dirt.png".rl, false, false).build()
                    )
                    .setDepthTestState(NO_DEPTH_TEST)
                    .setWriteMaskState(COLOR_WRITE)
                    .createCompositeState(false)
            )
        }
    }
}*/

object SelectingOverlay {
    fun draw(stack: PoseStack, aabb: AABB, partialTick: Float) {
        val zero = BoxRenderer.moveToZero(aabb)
        val minX = zero.minX.toFloat()
        val minY = zero.minY.toFloat()
        val minZ = zero.minZ.toFloat()
        val maxX = zero.maxX.toFloat()
        val maxY = zero.maxY.toFloat()
        val maxZ = zero.maxZ.toFloat()
        val p = mc.gameRenderer.mainCamera.position

        stack.pushPose()
        stack.translate(aabb.minX -p.x,aabb.minY -p.y,aabb.minZ -p.z)
        val matrix = stack.last().pose()

        RenderSystem.enableBlend()
        RenderSystem.depthMask(false)

        RenderSystem.blendFuncSeparate(
            GlStateManager.SourceFactor.SRC_ALPHA,
            GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
            GlStateManager.SourceFactor.ONE,
            GlStateManager.DestFactor.ZERO
        )
        val tessellator = Tesselator.getInstance()
        val bufferbuilder: BufferBuilder = tessellator.builder
        bufferbuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR_TEX)
        bufferbuilder.vertex(matrix, maxX, minY, minZ).color(1f, 1f, 1f, 1f).uv(1f, 1f).endVertex()
        bufferbuilder.vertex(matrix, maxX, maxY, minZ).color(1f, 1f, 1f, 1f).uv(1f, 0f).endVertex()
        bufferbuilder.vertex(matrix, minX, maxY, minZ).color(1f, 1f, 1f, 1f).uv(0f, 0f).endVertex()
        bufferbuilder.vertex(matrix, minX, minY, minZ).color(1f, 1f, 1f, 1f).uv(0f, 1f).endVertex()

        bufferbuilder.vertex(matrix, maxX, minY, maxZ).color(1f, 1f, 1f, 1f).uv(1f, 1f).endVertex()
        bufferbuilder.vertex(matrix, maxX, maxY, maxZ).color(1f, 1f, 1f, 1f).uv(1f, 0f).endVertex()
        bufferbuilder.vertex(matrix, minX, maxY, maxZ).color(1f, 1f, 1f, 1f).uv(0f, 0f).endVertex()
        bufferbuilder.vertex(matrix, minX, minY, maxZ).color(1f, 1f, 1f, 1f).uv(0f, 1f).endVertex()

        bufferbuilder.vertex(matrix, minX, maxY, minZ).color(1f, 1f, 1f, 1f).uv(1f, 1f).endVertex()
        bufferbuilder.vertex(matrix, minX, maxY, maxZ).color(1f, 1f, 1f, 1f).uv(1f, 0f).endVertex()
        bufferbuilder.vertex(matrix, minX, minY, maxZ).color(1f, 1f, 1f, 1f).uv(0f, 0f).endVertex()
        bufferbuilder.vertex(matrix, minX, minY, minZ).color(1f, 1f, 1f, 1f).uv(0f, 1f).endVertex()

        bufferbuilder.vertex(matrix, maxX, minY, minZ).color(1f, 1f, 1f, 1f).uv(0f, 1f).endVertex()
        bufferbuilder.vertex(matrix, maxX, minY, maxZ).color(1f, 1f, 1f, 1f).uv(0f, 0f).endVertex()
        bufferbuilder.vertex(matrix, maxX, maxY, maxZ).color(1f, 1f, 1f, 1f).uv(1f, 0f).endVertex()
        bufferbuilder.vertex(matrix, maxX, maxY, minZ).color(1f, 1f, 1f, 1f).uv(1f, 1f).endVertex()

        bufferbuilder.vertex(matrix, maxX, minY, minZ).color(1f, 1f, 1f, 1f).uv(1f, 1f).endVertex()
        bufferbuilder.vertex(matrix, maxX, minY, maxZ).color(1f, 1f, 1f, 1f).uv(1f, 0f).endVertex()
        bufferbuilder.vertex(matrix, minX, minY, maxZ).color(1f, 1f, 1f, 1f).uv(0f, 0f).endVertex()
        bufferbuilder.vertex(matrix, minX, minY, minZ).color(1f, 1f, 1f, 1f).uv(0f, 1f).endVertex()

        bufferbuilder.vertex(matrix, maxX, maxY, minZ).color(1f, 1f, 1f, 1f).uv(1f, 1f).endVertex()
        bufferbuilder.vertex(matrix, maxX, maxY, maxZ).color(1f, 1f, 1f, 1f).uv(1f, 0f).endVertex()
        bufferbuilder.vertex(matrix, minX, maxY, maxZ).color(1f, 1f, 1f, 1f).uv(0f, 0f).endVertex()
        bufferbuilder.vertex(matrix, minX, maxY, minZ).color(1f, 1f, 1f, 1f).uv(0f, 1f).endVertex()

        val old = RenderSystem.getShader()
        RenderSystem.setShader(ModShaders::BARRIER)
        ModShaders.BARRIER.getUniform("Time")?.set((TickHandler.currentTicks+Minecraft.getInstance().partialTick) / 200f)
        tessellator.end()
        RenderSystem.setShader { old }
        stack.popPose()
    }
}