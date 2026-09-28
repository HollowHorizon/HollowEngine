package ru.hollowhorizon.hollowengine.client.ui.render

import com.mojang.blaze3d.systems.RenderSystem
import org.joml.Matrix4f
import org.lwjgl.opengl.GL33

/**
 * Runs [draw], which renders into a target of its own from inside a UI draw, with UI-state was
 * drawing in set aside and put back afterward.
 */
internal inline fun withOwnRenderTarget(draw: () -> Unit) {
    val read = GL33.glGetInteger(GL33.GL_READ_FRAMEBUFFER_BINDING)
    val write = GL33.glGetInteger(GL33.GL_DRAW_FRAMEBUFFER_BINDING)
    val viewport = IntArray(4).also { GL33.glGetIntegerv(GL33.GL_VIEWPORT, it) }
    val scissorBox = IntArray(4).also { GL33.glGetIntegerv(GL33.GL_SCISSOR_BOX, it) }
    val scissor = GL33.glIsEnabled(GL33.GL_SCISSOR_TEST)
    val clearColor = FloatArray(4).also { GL33.glGetFloatv(GL33.GL_COLOR_CLEAR_VALUE, it) }
    val alpha = uiBlendWritesAlpha
    val projection = Matrix4f(RenderSystem.getProjectionMatrix())
    val sorting = RenderSystem.getVertexSorting()
    val modelView = RenderSystem.getModelViewStack()
    modelView.pushMatrix()
    try {
        disableScissor()
        RenderSystem.colorMask(true, true, true, true)
        draw()
    } finally {
        RenderSystem.setProjectionMatrix(projection, sorting)
        modelView.popMatrix()
        RenderSystem.applyModelViewMatrix()
        GL33.glBindFramebuffer(GL33.GL_READ_FRAMEBUFFER, read)
        GL33.glBindFramebuffer(GL33.GL_DRAW_FRAMEBUFFER, write)
        RenderSystem.viewport(viewport[0], viewport[1], viewport[2], viewport[3])
        GL33.glScissor(scissorBox[0], scissorBox[1], scissorBox[2], scissorBox[3])
        if (scissor) GL33.glEnable(GL33.GL_SCISSOR_TEST)
        GL33.glClearColor(clearColor[0], clearColor[1], clearColor[2], clearColor[3])
        uiWriteAlpha(alpha)
    }
}
