package ru.hollowhorizon.hollowengine.client.ui.render

import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL12
import org.lwjgl.opengl.GL30

internal class UiFramebufferPool {
    private val framebuffers = mutableListOf<UiFramebuffer>()

    fun acquire(width: Int, height: Int, exclude: UiFramebuffer? = null): UiFramebuffer {
        val framebuffer = framebuffers.firstOrNull {
            it !== exclude && !it.inUse && it.width == width && it.height == height
        } ?: UiFramebuffer(width, height).also {
            framebuffers += it
        }
        framebuffer.inUse = true
        return framebuffer
    }

    fun release(framebuffer: UiFramebuffer) {
        framebuffer.inUse = false
    }

    fun close() {
        framebuffers.forEach(UiFramebuffer::close)
        framebuffers.clear()
    }
}

internal class UiFramebuffer(
    val width: Int,
    val height: Int,
) {
    val framebuffer: Int = GL30.glGenFramebuffers()
    val texture: Int = GL11.glGenTextures()
    private val depth: Int = GL30.glGenRenderbuffers()
    var inUse: Boolean = false

    init {
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer)
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE)
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, width, height, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, 0L)
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, texture, 0)
        GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, depth)
        GL30.glRenderbufferStorage(GL30.GL_RENDERBUFFER, GL30.GL_DEPTH_COMPONENT24, width, height)
        GL30.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL30.GL_RENDERBUFFER, depth)
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0)
    }

    fun bind() {
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer)
    }

    fun close() {
        GL30.glDeleteFramebuffers(framebuffer)
        GL11.glDeleteTextures(texture)
        GL30.glDeleteRenderbuffers(depth)
    }
}
