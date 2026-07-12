package ru.hollowhorizon.hollowengine.addons.video.screen

import com.mojang.blaze3d.platform.GlStateManager
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.BufferUploader
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.Tesselator
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.renderer.GameRenderer
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL12
import ru.hollowhorizon.hollowengine.addons.video.decode.RgbVideoFrame
import java.nio.ByteBuffer
import kotlin.math.min

class VideoTextureRenderer : AutoCloseable {
    private var texture = 0
    private var width = 0
    private var height = 0
    private var rgbaPixels: ByteBuffer? = null

    fun upload(frame: RgbVideoFrame) {
        ensureTexture(frame.width, frame.height)
        val pixels = rgbaPixels ?: error("Video texture buffer was not initialized")
        copyRgbToRgba(frame.pixels, pixels, frame.width * frame.height)
        pixels.position(0)
        GlStateManager._bindTexture(texture)
        GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 0)
        GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, 0)
        GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 4)

        GL11.glTexSubImage2D(
            GL11.GL_TEXTURE_2D,
            0,
            0,
            0,
            frame.width,
            frame.height,
            GL11.GL_RGBA,
            GL11.GL_UNSIGNED_BYTE,
            pixels,
        )
    }

    fun render(screenWidth: Int, screenHeight: Int) {
        if (texture == 0 || width <= 0 || height <= 0) return
        val scale = min(screenWidth.toFloat() / width, screenHeight.toFloat() / height)
        val drawWidth = width * scale
        val drawHeight = height * scale
        val left = (screenWidth - drawWidth) * 0.5f
        val top = (screenHeight - drawHeight) * 0.5f
        val right = left + drawWidth
        val bottom = top + drawHeight

        RenderSystem.setShader { GameRenderer.getPositionTexColorShader() }
        GlStateManager._bindTexture(texture)
        RenderSystem.setShaderTexture(0, texture)
        RenderSystem.enableBlend()
        RenderSystem.defaultBlendFunc()

        val buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR)
        buffer.addVertex(left, top, 0f).setUv(0f, 0f).setColor(1f, 1f, 1f, 1f)
        buffer.addVertex(left, bottom, 0f).setUv(0f, 1f).setColor(1f, 1f, 1f, 1f)
        buffer.addVertex(right, bottom, 0f).setUv(1f, 1f).setColor(1f, 1f, 1f, 1f)
        buffer.addVertex(right, top, 0f).setUv(1f, 0f).setColor(1f, 1f, 1f, 1f)
        BufferUploader.drawWithShader(buffer.buildOrThrow())
    }

    private fun ensureTexture(nextWidth: Int, nextHeight: Int) {
        if (texture != 0 && width == nextWidth && height == nextHeight) return
        if (texture == 0) texture = GL11.glGenTextures()
        width = nextWidth
        height = nextHeight
        rgbaPixels = ByteBuffer.allocateDirect(width * height * RgbaBytesPerPixel)
        GlStateManager._bindTexture(texture)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE)
        GL11.glTexImage2D(
            GL11.GL_TEXTURE_2D,
            0,
            GL11.GL_RGBA,
            width,
            height,
            0,
            GL11.GL_RGBA,
            GL11.GL_UNSIGNED_BYTE,
            null as ByteBuffer?,
        )
    }

    private fun copyRgbToRgba(rgb: ByteBuffer, rgba: ByteBuffer, pixels: Int) {
        rgba.clear()
        val source = rgb.asReadOnlyBuffer()
        source.position(0)
        repeat(pixels) { index ->
            val rgbIndex = index * RgbBytesPerPixel
            rgba.put(source.get(rgbIndex))
            rgba.put(source.get(rgbIndex + 1))
            rgba.put(source.get(rgbIndex + 2))
            rgba.put(OpaqueAlpha)
        }
        rgba.flip()
    }

    override fun close() {
        if (texture != 0) {
            GL11.glDeleteTextures(texture)
            texture = 0
        }
        rgbaPixels = null
        width = 0
        height = 0
    }
}

private const val RgbBytesPerPixel = 3
private const val RgbaBytesPerPixel = 4
private const val OpaqueAlpha: Byte = -1