package ru.hollowhorizon.hollowengine.client.ui.render

import com.mojang.blaze3d.platform.GlStateManager
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.BufferUploader
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.Tesselator
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.renderer.GameRenderer
import net.minecraft.resources.ResourceLocation
import ru.hollowhorizon.hollowengine.client.ui.UiColor
import ru.hollowhorizon.hollowengine.client.ui.UiFilterChain
import ru.hollowhorizon.hollowengine.client.ui.UiImageFit
import ru.hollowhorizon.hollowengine.client.ui.UiMatrix4
import ru.hollowhorizon.hollowengine.common.registry.ModShaders

internal object UiTextureEffects {
    fun drawTexture(
        texture: Int,
        width: Float,
        height: Float,
        transform: UiMatrix4,
        opacity: Float,
        flipY: Boolean,
        filter: UiFilterChain = UiFilterChain.Empty,
        textureWidth: Float = width,
        textureHeight: Float = height,
    ) {
        GlStateManager._bindTexture(texture)
        RenderSystem.setShaderTexture(0, texture)
        drawTexturedQuad(width, height, transform, opacity, flipY, filter = filter, textureWidth = textureWidth, textureHeight = textureHeight)
    }

    fun drawTexturedQuad(
        width: Float,
        height: Float,
        transform: UiMatrix4,
        opacity: Float,
        flipY: Boolean,
        fit: UiImageFit = UiImageFit.STRETCH,
        texture: ResourceLocation? = null,
        filter: UiFilterChain = UiFilterChain.Empty,
        textureWidth: Float = width,
        textureHeight: Float = height,
    ) {
        setTextureShader(filter, textureWidth, textureHeight)
        val tessellator = Tesselator.getInstance()
        val buffer = tessellator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR)
        val placement = imagePlacement(width, height, fit, texture)
        val corners = localCorners(placement.width, placement.height, transform * UiMatrix4.translation(placement.x, placement.y, 0f))
        val top = if (flipY) placement.v1 else placement.v0
        val bottom = if (flipY) placement.v0 else placement.v1
        val tint = UiColor.White.withOpacity(opacity).filtered(filter)
        buffer.addVertex(corners[0].x, corners[0].y, corners[0].z).setUv(placement.u0, top).setColor(tint.red, tint.green, tint.blue, tint.alpha)
        buffer.addVertex(corners[1].x, corners[1].y, corners[1].z).setUv(placement.u0, bottom).setColor(tint.red, tint.green, tint.blue, tint.alpha)
        buffer.addVertex(corners[2].x, corners[2].y, corners[2].z).setUv(placement.u1, bottom).setColor(tint.red, tint.green, tint.blue, tint.alpha)
        buffer.addVertex(corners[3].x, corners[3].y, corners[3].z).setUv(placement.u1, top).setColor(tint.red, tint.green, tint.blue, tint.alpha)
        BufferUploader.drawWithShader(buffer.buildOrThrow())
    }

    fun drawTexturedRegion(
        texture: Int,
        width: Float,
        height: Float,
        transform: UiMatrix4,
        opacity: Float,
        u0: Float,
        v0: Float,
        u1: Float,
        v1: Float,
        flipY: Boolean,
        filter: UiFilterChain,
        textureWidth: Int,
        textureHeight: Int,
    ) {
        GlStateManager._bindTexture(texture)
        RenderSystem.setShaderTexture(0, texture)
        setTextureShader(filter, textureWidth.toFloat(), textureHeight.toFloat())
        val tessellator = Tesselator.getInstance()
        val buffer = tessellator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR)
        val corners = localCorners(width, height, transform)
        val top = if (flipY) v1 else v0
        val bottom = if (flipY) v0 else v1
        val tint = UiColor.White.withOpacity(opacity)
        buffer.addVertex(corners[0].x, corners[0].y, corners[0].z).setUv(u0, top).setColor(tint.red, tint.green, tint.blue, tint.alpha)
        buffer.addVertex(corners[1].x, corners[1].y, corners[1].z).setUv(u0, bottom).setColor(tint.red, tint.green, tint.blue, tint.alpha)
        buffer.addVertex(corners[2].x, corners[2].y, corners[2].z).setUv(u1, bottom).setColor(tint.red, tint.green, tint.blue, tint.alpha)
        buffer.addVertex(corners[3].x, corners[3].y, corners[3].z).setUv(u1, top).setColor(tint.red, tint.green, tint.blue, tint.alpha)
        BufferUploader.drawWithShader(buffer.buildOrThrow())
    }

    private fun setTextureShader(filter: UiFilterChain, textureWidth: Float, textureHeight: Float) {
        val effectShader = ModShaders.UI_EFFECT
        if (filter.effects.isEmpty() || effectShader == null) {
            RenderSystem.setShader(GameRenderer::getPositionTexColorShader)
            return
        }
        RenderSystem.setShader { effectShader }
        effectShader.getUniform("Grayscale")?.set(filter.grayscaleAmount())
        effectShader.getUniform("BlurRadius")?.set(filter.blurRadius())
        effectShader.getUniform("TexelSize")?.set(1f / textureWidth.coerceAtLeast(1f), 1f / textureHeight.coerceAtLeast(1f))
    }
}
