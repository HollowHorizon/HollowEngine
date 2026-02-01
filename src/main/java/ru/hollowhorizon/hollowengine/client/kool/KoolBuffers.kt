package ru.hollowhorizon.hollowengine.client.kool

import com.mojang.blaze3d.pipeline.RenderTarget
import com.mojang.blaze3d.pipeline.TextureTarget
import de.fabmax.kool.pipeline.*
import de.fabmax.kool.pipeline.backend.gl.GlTexture
import de.fabmax.kool.pipeline.backend.gl.LoadedTextureGl
import net.minecraft.client.Minecraft
import ru.hollowhorizon.hollowengine.client.HollowCoreClient
import ru.hollowhorizon.hollowengine.client.kool.gl.MCGlApi

internal val guiFramebuffer = TextureTarget(512, 512, true, Minecraft.ON_OSX)

val WINDOW_BUFFER by lazy { createFramebufferTexture(guiFramebuffer) }

fun createFramebufferTexture(texture: RenderTarget, depth: Boolean = false) = Texture2d(
    mipMapping = MipMapping.Off,
    samplerSettings = SamplerSettings().clamped().nearest(),
    format = if(depth) TexFormat.R_F32 else TexFormat.RGBA,
).apply {
    val estSize = Texture.estimatedTexSize(texture.width, texture.height, 1, 1, 4).toLong()
    gpuTexture = LoadedTextureGl(
        MCGlApi.TEXTURE_2D,
        GlTexture(if(depth) texture.depthTextureId else texture.colorTextureId),
        MCGlApi.backend,
        this,
        estSize
    ).apply {
        width = texture.width
        height = texture.height
        this.depth = 1
    }
}

fun onResize(width: Int, height: Int) {
    (WINDOW_BUFFER.gpuTexture as? LoadedTextureGl)?.apply {
        this.width = width
        this.height = height
    }
    (HollowCoreClient.img.gpuTexture as? LoadedTextureGl)?.apply {
        this.width = width
        this.height = height
    }
}
