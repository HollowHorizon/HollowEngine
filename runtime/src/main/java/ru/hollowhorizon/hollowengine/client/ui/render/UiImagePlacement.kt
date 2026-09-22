package ru.hollowhorizon.hollowengine.client.ui.render

import com.mojang.blaze3d.platform.GlStateManager
import net.minecraft.client.Minecraft
import net.minecraft.resources.ResourceLocation
import org.lwjgl.opengl.GL11
import ru.hollowhorizon.hollowengine.client.ui.style.UiImageFit
import ru.hollowhorizon.hollowengine.client.ui.style.UiImageUv
import ru.hollowhorizon.hollowengine.client.ui.style.UiUvRect

internal data class ImagePlacement(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val u0: Float = 0f,
    val v0: Float = 0f,
    val u1: Float = 1f,
    val v1: Float = 1f,
) {
    fun u(fraction: Float): Float = u0 + (u1 - u0) * fraction

    fun v(fraction: Float): Float = v0 + (v1 - v0) * fraction

    fun within(region: UiUvRect): ImagePlacement {
        if (region == UiUvRect.Full) return this
        return copy(
            u0 = region.u0 + u0 * region.width,
            v0 = region.v0 + v0 * region.height,
            u1 = region.u0 + u1 * region.width,
            v1 = region.v0 + v1 * region.height,
        )
    }
}

internal fun imagePlacement(
    width: Float,
    height: Float,
    fit: UiImageFit,
    texture: ResourceLocation?,
    uv: UiImageUv = UiImageUv.Full,
): ImagePlacement {
    return imagePlacement(width, height, fit, texture?.let(::textureSize), uv)
}

internal fun imagePlacement(
    width: Float,
    height: Float,
    fit: UiImageFit,
    size: Pair<Float, Float>?,
    uv: UiImageUv = UiImageUv.Full,
): ImagePlacement {
    if (size == null) {
        return ImagePlacement(0f, 0f, width, height).within(uv.resolve(1f, 1f))
    }
    val region = uv.resolve(size.first, size.second)
    val sourceWidth = size.first * region.width
    val sourceHeight = size.second * region.height
    val sourceAspect = sourceWidth / sourceHeight
    val targetAspect = width / height
    val placement = when (fit) {
        UiImageFit.STRETCH,
        UiImageFit.NINE_SLICE,
        UiImageFit.THREE_SLICE_VERTICAL,
        UiImageFit.THREE_SLICE_HORIZONTAL,
            -> ImagePlacement(0f, 0f, width, height)

        UiImageFit.NONE -> ImagePlacement(
            (width - sourceWidth) * 0.5f,
            (height - sourceHeight) * 0.5f,
            sourceWidth,
            sourceHeight,
        )

        UiImageFit.CONTAIN -> containPlacement(width, height, sourceAspect, targetAspect)
        UiImageFit.COVER -> coverPlacement(width, height, sourceAspect, targetAspect)
    }
    return placement.within(region)
}

private fun containPlacement(width: Float, height: Float, sourceAspect: Float, targetAspect: Float): ImagePlacement {
    val drawWidth: Float
    val drawHeight: Float
    if (sourceAspect > targetAspect) {
        drawWidth = width
        drawHeight = width / sourceAspect
    } else {
        drawHeight = height
        drawWidth = height * sourceAspect
    }
    return ImagePlacement((width - drawWidth) * 0.5f, (height - drawHeight) * 0.5f, drawWidth, drawHeight)
}

private fun coverPlacement(width: Float, height: Float, sourceAspect: Float, targetAspect: Float): ImagePlacement {
    val cropX: Float
    val cropY: Float
    if (sourceAspect > targetAspect) {
        cropX = (1f - targetAspect / sourceAspect) * 0.5f
        cropY = 0f
    } else {
        cropX = 0f
        cropY = (1f - sourceAspect / targetAspect) * 0.5f
    }
    return ImagePlacement(0f, 0f, width, height, cropX, cropY, 1f - cropX, 1f - cropY)
}

internal fun textureSize(location: ResourceLocation): Pair<Float, Float>? {
    val texture = Minecraft.getInstance().textureManager.getTexture(location)
    val id = texture.id
    GlStateManager._bindTexture(id)
    val width = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH)
    val height = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT)
    if (width <= 0 || height <= 0) return null
    return width.toFloat() to height.toFloat()
}
