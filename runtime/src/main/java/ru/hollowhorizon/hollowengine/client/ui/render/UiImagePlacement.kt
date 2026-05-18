package ru.hollowhorizon.hollowengine.client.ui.render

import com.mojang.blaze3d.platform.GlStateManager
import net.minecraft.client.Minecraft
import net.minecraft.resources.ResourceLocation
import org.lwjgl.opengl.GL11
import ru.hollowhorizon.hollowengine.client.ui.UiImageFit

internal data class ImagePlacement(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val u0: Float = 0f,
    val v0: Float = 0f,
    val u1: Float = 1f,
    val v1: Float = 1f,
)

internal fun imagePlacement(width: Float, height: Float, fit: UiImageFit, texture: ResourceLocation?): ImagePlacement {
    val size = texture?.let(::textureSize) ?: return ImagePlacement(0f, 0f, width, height)
    val sourceAspect = size.first / size.second
    val targetAspect = width / height
    return when (fit) {
        UiImageFit.STRETCH,
        UiImageFit.NINE_SLICE,
        UiImageFit.THREE_SLICE_VERTICAL,
        UiImageFit.THREE_SLICE_HORIZONTAL -> ImagePlacement(0f, 0f, width, height)
        UiImageFit.NONE -> ImagePlacement((width - size.first) * 0.5f, (height - size.second) * 0.5f, size.first, size.second)
        UiImageFit.CONTAIN -> containPlacement(width, height, sourceAspect, targetAspect)
        UiImageFit.COVER -> coverPlacement(width, height, sourceAspect, targetAspect)
    }
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
