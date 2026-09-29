package ru.hollowhorizon.hollowengine.client.ui.ide.files.shadergraph

import com.mojang.blaze3d.pipeline.TextureTarget
import com.mojang.blaze3d.platform.GlStateManager
import net.minecraft.client.Minecraft
import org.lwjgl.opengl.GL33
import ru.hollowhorizon.hollowengine.common.utils.rl

/**
 * The screenshot a post effect is previewed over, copied into a target of its own and turned over on
 * the way.
 */
internal class ShaderPreviewScene {
    private var location: String? = null
    private var copy: TextureTarget? = null

    /** The copy of the screenshot at [location]; render thread, with no scissor on. */
    fun texture(location: String): Int {
        val current = copy
        if (current != null && location == this.location) return current.colorTextureId
        release()
        this.location = location

        val source = Minecraft.getInstance().textureManager.getTexture(location.rl).id
        GlStateManager._bindTexture(source)
        val width = GL33.glGetTexLevelParameteri(GL33.GL_TEXTURE_2D, 0, GL33.GL_TEXTURE_WIDTH).coerceAtLeast(1)
        val height = GL33.glGetTexLevelParameteri(GL33.GL_TEXTURE_2D, 0, GL33.GL_TEXTURE_HEIGHT).coerceAtLeast(1)
        val target = TextureTarget(width, height, false, Minecraft.ON_OSX)
        target.setFilterMode(GL33.GL_LINEAR)

        val read = GlStateManager.glGenFramebuffers()
        GlStateManager._glBindFramebuffer(GL33.GL_READ_FRAMEBUFFER, read)
        GL33.glFramebufferTexture2D(GL33.GL_READ_FRAMEBUFFER, GL33.GL_COLOR_ATTACHMENT0, GL33.GL_TEXTURE_2D, source, 0)
        GlStateManager._glBindFramebuffer(GL33.GL_DRAW_FRAMEBUFFER, target.frameBufferId)
        GL33.glBlitFramebuffer(0, 0, width, height, 0, height, width, 0, GL33.GL_COLOR_BUFFER_BIT, GL33.GL_NEAREST)
        GlStateManager._glBindFramebuffer(GL33.GL_READ_FRAMEBUFFER, 0)
        GlStateManager._glDeleteFramebuffers(read)

        copy = target
        return target.colorTextureId
    }

    fun release() {
        copy?.destroyBuffers()
        copy = null
        location = null
    }
}
