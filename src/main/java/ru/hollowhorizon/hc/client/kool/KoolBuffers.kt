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

package ru.hollowhorizon.hc.client.kool

import com.mojang.blaze3d.pipeline.RenderTarget
import com.mojang.blaze3d.pipeline.TextureTarget
import de.fabmax.kool.pipeline.MipMapping
import de.fabmax.kool.pipeline.SamplerSettings
import de.fabmax.kool.pipeline.Texture
import de.fabmax.kool.pipeline.Texture2d
import de.fabmax.kool.pipeline.backend.gl.GlTexture
import de.fabmax.kool.pipeline.backend.gl.LoadedTextureGl
import net.minecraft.client.Minecraft
import ru.hollowhorizon.hc.HollowCore
import ru.hollowhorizon.hc.client.HollowCoreClient
import ru.hollowhorizon.hc.client.kool.gl.MCGlApi

internal val guiFramebuffer = TextureTarget(512, 512, true, Minecraft.ON_OSX)

val WINDOW_BUFFER by lazy { createFramebufferTexture(guiFramebuffer) }

fun createFramebufferTexture(texture: RenderTarget) = Texture2d(
    mipMapping = MipMapping.Off,
    samplerSettings = SamplerSettings().clamped().nearest()
).apply {
    val estSize = Texture.estimatedTexSize(texture.width, texture.height, 1, 1, 4).toLong()
    gpuTexture = LoadedTextureGl(
        MCGlApi.TEXTURE_2D,
        GlTexture(texture.colorTextureId),
        MCGlApi.backend,
        this,
        estSize
    ).apply {
        width = texture.width
        height = texture.height
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
