package ru.hollowhorizon.hollowengine.client.kool

import com.mojang.blaze3d.pipeline.RenderTarget
import com.mojang.blaze3d.pipeline.TextureTarget
import de.fabmax.kool.pipeline.MipMapping
import de.fabmax.kool.pipeline.SamplerSettings
import de.fabmax.kool.pipeline.Texture
import de.fabmax.kool.pipeline.Texture2d
import de.fabmax.kool.pipeline.backend.gl.GlTexture
import de.fabmax.kool.pipeline.backend.gl.LoadedTextureGl
import net.minecraft.client.Minecraft
import ru.hollowhorizon.hollowengine.client.kool.gl.MCGlApi

internal val guiFramebuffer = TextureTarget(512, 512, true, Minecraft.ON_OSX)

val WINDOW_BUFFER by lazy { createFramebufferTexture(guiFramebuffer) }
val CUTSCENE_VIEWPORT by lazy { createFramebufferTexture(Minecraft.getInstance().mainRenderTarget) }

fun createFramebufferTexture(texture: RenderTarget) = Texture2d(
    mipMapping = MipMapping.Off,
    samplerSettings = SamplerSettings().clamped().nearest()
).apply {
    gpuTexture = createGpuTexture(texture)
}

private fun Texture2d.createGpuTexture(
    texture: RenderTarget,
): LoadedTextureGl = LoadedTextureGl(
    MCGlApi.TEXTURE_2D,
    GlTexture(texture.colorTextureId),
    MCGlApi.backend,
    this,
    Texture.estimatedTexSize(texture.width, texture.height, 1, 1, 4).toLong()
).apply {
    width = texture.width
    height = texture.height
}

fun onResize(width: Int, height: Int) {
    WINDOW_BUFFER.resize(width, height)
    CUTSCENE_VIEWPORT.resize(width, height)
}

fun Texture2d.resize(width: Int, height: Int) {
    (gpuTexture as LoadedTextureGl).let {
        it.width = width
        it.height = height
    }
}

fun Texture2d.rebindIfDepth(target: RenderTarget) {
    (gpuTexture as LoadedTextureGl).let {
        if (it.glTexture.handle != target.colorTextureId) {
            gpuTexture = createGpuTexture(target)
        }
    }
}

fun cutsceneViewportTextureId(): Int {
    CUTSCENE_VIEWPORT.rebindIfDepth(Minecraft.getInstance().mainRenderTarget)
    return (CUTSCENE_VIEWPORT.gpuTexture as LoadedTextureGl).glTexture.handle
}
