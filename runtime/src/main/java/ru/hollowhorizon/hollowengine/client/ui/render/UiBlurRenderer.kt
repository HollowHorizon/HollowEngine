package ru.hollowhorizon.hollowengine.client.ui.render

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.VertexSorting
import org.joml.Matrix4f
import org.lwjgl.opengl.GL11
import ru.hollowhorizon.hollowengine.client.ui.style.UiFilterChain
import ru.hollowhorizon.hollowengine.client.ui.style.UiFilterEffect
import ru.hollowhorizon.hollowengine.client.ui.UiMatrix4
import ru.hollowhorizon.hollowengine.client.utils.setIdentity

internal fun blurTexture(
    framebuffers: UiFramebufferPool,
    sourceTexture: Int,
    width: Int,
    height: Int,
    radius: Float,
    exclude: UiFramebuffer? = null,
): UiFramebuffer {
    val horizontal = framebuffers.acquire(width, height, exclude)
    val vertical = framebuffers.acquire(width, height, horizontal)
    val blurFilter = UiFilterChain(listOf(UiFilterEffect.Blur(radius)))
    renderBlurPass(horizontal, sourceTexture, width, height, blurFilter, horizontal = true)
    renderBlurPass(vertical, horizontal.texture, width, height, blurFilter, horizontal = false)
    framebuffers.release(horizontal)
    return vertical
}

private fun renderBlurPass(target: UiFramebuffer, sourceTexture: Int, width: Int, height: Int, filter: UiFilterChain, horizontal: Boolean) {
    target.bind()
    GL11.glViewport(0, 0, width, height)
    configureBlurProjection(width.toFloat(), height.toFloat())
    configureUiBlend()
    GL11.glClearColor(0f, 0f, 0f, 0f)
    GL11.glClear(GL11.GL_COLOR_BUFFER_BIT)
    UiTextureEffects.drawTexture(
        sourceTexture,
        width.toFloat(),
        height.toFloat(),
        UiMatrix4.identity(),
        1f,
        flipY = false,
        filter = filter,
        textureWidth = width.toFloat(),
        textureHeight = height.toFloat(),
        blurDirectionX = if (horizontal) 1f else 0f,
        blurDirectionY = if (horizontal) 0f else 1f,
    )
}

private fun configureBlurProjection(width: Float, height: Float) {
    RenderSystem.setProjectionMatrix(
        Matrix4f().setOrtho(0f, width, height, 0f, -1000f, 1000f),
        VertexSorting.ORTHOGRAPHIC_Z,
    )
    val stack = RenderSystem.getModelViewStack()
    stack.setIdentity()
    RenderSystem.applyModelViewMatrix()
}
