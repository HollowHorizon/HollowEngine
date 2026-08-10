package ru.hollowhorizon.hollowengine.client.ui.render

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.VertexSorting
import org.joml.Matrix4f
import org.lwjgl.opengl.GL11
import ru.hollowhorizon.hollowengine.client.ui.UiMatrix4
import ru.hollowhorizon.hollowengine.client.ui.style.UiFilterChain
import ru.hollowhorizon.hollowengine.client.ui.style.UiFilterEffect
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
    renderBlurPass(horizontal, sourceTexture, width, height, width, height, blurFilter, horizontal = true)
    renderBlurPass(vertical, horizontal.texture, width, height, width, height, blurFilter, horizontal = false)
    framebuffers.release(horizontal)
    return vertical
}

/**
 * Blurs a grab of whatever is behind a `backdrop-filter`.
 */
internal fun blurBackdropTexture(
    workspace: UiBackdropBlurWorkspace,
    width: Int,
    height: Int,
    radius: Float,
): UiFramebuffer {
    val blurFilter = UiFilterChain(listOf(UiFilterEffect.Blur(radius)))
    renderBlurPass(
        workspace.intermediate, workspace.capture.texture, workspace.width, workspace.height,
        width, height, blurFilter, horizontal = true, opaqueSource = true,
    )
    renderBlurPass(
        workspace.capture, workspace.intermediate.texture, workspace.width, workspace.height,
        width, height, blurFilter, horizontal = false, opaqueSource = true,
    )
    return workspace.capture
}

private fun renderBlurPass(
    target: UiFramebuffer,
    sourceTexture: Int,
    sourceTextureWidth: Int,
    sourceTextureHeight: Int,
    width: Int,
    height: Int,
    filter: UiFilterChain,
    horizontal: Boolean,
    opaqueSource: Boolean = false,
) {
    target.bind()
    GL11.glViewport(0, 0, width, height)
    configureBlurProjection(width.toFloat(), height.toFloat())
    configureUiBlend()
    GL11.glClearColor(0f, 0f, 0f, 0f)
    GL11.glEnable(GL11.GL_SCISSOR_TEST)
    GL11.glScissor(0, 0, width, height)
    GL11.glClear(GL11.GL_COLOR_BUFFER_BIT)
    disableScissor()
    UiTextureEffects.drawTexturedRegion(
        texture = sourceTexture,
        width = width.toFloat(),
        height = height.toFloat(),
        transform = UiMatrix4.identity(),
        opacity = 1f,
        u0 = 0f,
        v0 = 0f,
        u1 = width / sourceTextureWidth.toFloat(),
        v1 = height / sourceTextureHeight.toFloat(),
        flipY = false,
        filter = filter,
        textureWidth = sourceTextureWidth,
        textureHeight = sourceTextureHeight,
        blurDirectionX = if (horizontal) 1f else 0f,
        blurDirectionY = if (horizontal) 0f else 1f,
        opaqueSource = opaqueSource,
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
