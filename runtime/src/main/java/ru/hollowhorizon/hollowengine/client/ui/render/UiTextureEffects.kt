package ru.hollowhorizon.hollowengine.client.ui.render

import com.mojang.blaze3d.platform.GlStateManager
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.*
import net.minecraft.client.renderer.GameRenderer
import net.minecraft.resources.ResourceLocation
import ru.hollowhorizon.hollowengine.client.ui.UiColor
import ru.hollowhorizon.hollowengine.client.ui.UiFilterChain
import ru.hollowhorizon.hollowengine.client.ui.UiImageFit
import ru.hollowhorizon.hollowengine.client.ui.UiMatrix4
import ru.hollowhorizon.hollowengine.common.registry.ModShaders
import kotlin.math.abs

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
        subdivisions: Int = 1,
        maskRadius: Float = 0f,
        maskPadding: Float = 0f,
        blurDirectionX: Float = 0f,
        blurDirectionY: Float = 0f,
    ) {
        GlStateManager._bindTexture(texture)
        RenderSystem.setShaderTexture(0, texture)
        drawTexturedQuad(
            width,
            height,
            transform,
            opacity,
            flipY,
            filter = filter,
            textureWidth = textureWidth,
            textureHeight = textureHeight,
            subdivisions = subdivisions,
            maskRadius = maskRadius,
            maskPadding = maskPadding,
            blurDirectionX = blurDirectionX,
            blurDirectionY = blurDirectionY,
        )
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
        subdivisions: Int = 1,
        maskRadius: Float = 0f,
        maskPadding: Float = 0f,
        blurDirectionX: Float = 0f,
        blurDirectionY: Float = 0f,
    ) {
        setTextureShader(
            filter,
            textureWidth,
            textureHeight,
            width,
            height,
            maskRadius,
            maskPadding,
            blurDirectionX,
            blurDirectionY
        )
        val tessellator = Tesselator.getInstance()
        val segments = subdivisions.coerceAtLeast(1)
        val buffer = tessellator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR)
        val placement = imagePlacement(width, height, fit, texture)
        val tint = UiColor.White.withOpacity(opacity).filtered(filter)
        val quadTransform = transform * UiMatrix4.translation(placement.x, placement.y, 0f)
        for (yIndex in 0 until segments) {
            val y0 = yIndex.toFloat() / segments.toFloat()
            val y1 = (yIndex + 1).toFloat() / segments.toFloat()
            for (xIndex in 0 until segments) {
                val x0 = xIndex.toFloat() / segments.toFloat()
                val x1 = (xIndex + 1).toFloat() / segments.toFloat()
                addTexturedVertex(
                    buffer,
                    quadTransform,
                    placement.width,
                    placement.height,
                    x0,
                    y0,
                    placement.u0,
                    placement.v0,
                    placement.u1,
                    placement.v1,
                    flipY,
                    tint
                )
                addTexturedVertex(
                    buffer,
                    quadTransform,
                    placement.width,
                    placement.height,
                    x0,
                    y1,
                    placement.u0,
                    placement.v0,
                    placement.u1,
                    placement.v1,
                    flipY,
                    tint
                )
                addTexturedVertex(
                    buffer,
                    quadTransform,
                    placement.width,
                    placement.height,
                    x1,
                    y1,
                    placement.u0,
                    placement.v0,
                    placement.u1,
                    placement.v1,
                    flipY,
                    tint
                )
                addTexturedVertex(
                    buffer,
                    quadTransform,
                    placement.width,
                    placement.height,
                    x1,
                    y0,
                    placement.u0,
                    placement.v0,
                    placement.u1,
                    placement.v1,
                    flipY,
                    tint
                )
            }
        }
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
        maskRadius: Float = 0f,
        maskScale: Float = 0f,
        maskPadding: Float = 0f,
    ) {
        GlStateManager._bindTexture(texture)
        RenderSystem.setShaderTexture(0, texture)
        setTextureShader(
            filter,
            textureWidth.toFloat(),
            textureHeight.toFloat(),
            logicalWidth = width,
            logicalHeight = height,
            maskRadius = maskRadius,
            maskPadding = maskPadding,
            maskScale = maskScale,
            maskU = minOf(u0, u1),
            maskV = minOf(v0, v1),
            maskWidth = abs(u1 - u0),
            maskHeight = abs(v1 - v0),
        )
        val tessellator = Tesselator.getInstance()
        val buffer = tessellator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR)
        val corners = localCorners(width, height, transform)
        val top = if (flipY) v1 else v0
        val bottom = if (flipY) v0 else v1
        val tint = UiColor.White.withOpacity(opacity)
        buffer.addVertex(corners[0].x, corners[0].y, corners[0].z).setUv(u0, top)
            .setColor(tint.red, tint.green, tint.blue, tint.alpha)
        buffer.addVertex(corners[1].x, corners[1].y, corners[1].z).setUv(u0, bottom)
            .setColor(tint.red, tint.green, tint.blue, tint.alpha)
        buffer.addVertex(corners[2].x, corners[2].y, corners[2].z).setUv(u1, bottom)
            .setColor(tint.red, tint.green, tint.blue, tint.alpha)
        buffer.addVertex(corners[3].x, corners[3].y, corners[3].z).setUv(u1, top)
            .setColor(tint.red, tint.green, tint.blue, tint.alpha)
        BufferUploader.drawWithShader(buffer.buildOrThrow())
    }

    private fun setTextureShader(
        filter: UiFilterChain,
        textureWidth: Float,
        textureHeight: Float,
        logicalWidth: Float = textureWidth,
        logicalHeight: Float = textureHeight,
        maskRadius: Float = 0f,
        maskPadding: Float = 0f,
        maskScale: Float = 0f,
        maskU: Float? = null,
        maskV: Float? = null,
        maskWidth: Float? = null,
        maskHeight: Float? = null,
        blurDirectionX: Float = 0f,
        blurDirectionY: Float = 0f,
    ) {
        val effectShader = ModShaders.UI_EFFECT
        val hasMask = maskRadius > 0f
        if ((filter.effects.isEmpty() && !hasMask) || effectShader == null) {
            RenderSystem.setShader(GameRenderer::getPositionTexColorShader)
            configureUiBlend()
            return
        }
        RenderSystem.setShader { effectShader }
        configureUiBlend()
        effectShader.getUniform("Grayscale")?.set(filter.grayscaleAmount())
        effectShader.getUniform("BlurRadius")?.set(filter.blurRadius())
        effectShader.getUniform("BlurDirection")?.set(blurDirectionX, blurDirectionY)
        effectShader.getUniform("TexelSize")
            ?.set(1f / textureWidth.coerceAtLeast(1f), 1f / textureHeight.coerceAtLeast(1f))
        val radiusScale = maskScale.takeIf { it > 0f }
            ?: (((textureWidth / logicalWidth.coerceAtLeast(1f)) +
                    (textureHeight / logicalHeight.coerceAtLeast(1f))) * 0.5f)
        val padU = maskPadding / logicalWidth.coerceAtLeast(1f)
        val padV = maskPadding / logicalHeight.coerceAtLeast(1f)
        val finalMaskU = if (maskU != null) maskU + padU else padU
        val finalMaskV = if (maskV != null) maskV + padV else padV
        val finalMaskW = (if (maskWidth != null) maskWidth - padU * 2f else (1f - padU * 2f)).coerceAtLeast(0f)
        val finalMaskH = (if (maskHeight != null) maskHeight - padV * 2f else (1f - padV * 2f)).coerceAtLeast(0f)
        effectShader.getUniform("MaskRect")?.set(finalMaskU, finalMaskV, finalMaskW, finalMaskH)
        effectShader.getUniform("MaskRadius")?.set(maskRadius * radiusScale)
        effectShader.getUniform("MaskSoftness")?.set(1.25f * radiusScale)
    }

    private fun addTexturedVertex(
        buffer: BufferBuilder,
        transform: UiMatrix4,
        width: Float,
        height: Float,
        xProgress: Float,
        yProgress: Float,
        u0: Float,
        v0: Float,
        u1: Float,
        v1: Float,
        flipY: Boolean,
        tint: UiColor,
    ) {
        val point = transform.transform(width * xProgress, height * yProgress)
        val u = u0 + (u1 - u0) * xProgress
        val baseV = v0 + (v1 - v0) * yProgress
        val flippedV = v1 + (v0 - v1) * yProgress
        val v = if (flipY) flippedV else baseV
        buffer.addVertex(point.x, point.y, point.z).setUv(u, v).setColor(tint.red, tint.green, tint.blue, tint.alpha)
    }
}
