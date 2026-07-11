package ru.hollowhorizon.hollowengine.client.kool.gl

import com.mojang.blaze3d.platform.GlStateManager
import com.mojang.blaze3d.systems.RenderSystem
import de.fabmax.kool.modules.ui2.UiScale
import de.fabmax.kool.pipeline.CullMethod
import de.fabmax.kool.pipeline.DepthCompareOp
import de.fabmax.kool.pipeline.backend.gl.glOp
import de.fabmax.kool.scene.Scene
import kotlinx.coroutines.runBlocking
import org.lwjgl.opengl.GL30
import org.lwjgl.opengl.GL33
import ru.hollowhorizon.hollowengine.client.kool.KoolHooks
import ru.hollowhorizon.hollowengine.client.kool.KoolManager
import ru.hollowhorizon.hollowengine.common.events.ClientOnly
import ru.hollowhorizon.hollowengine.common.events.SubscribeEvent
import ru.hollowhorizon.hollowengine.common.events.client.render.RenderTickEvent

@ClientOnly
object GlContext {
    private var activeTexture = -1
    private var bindingTexture = -1
    private var framebufferBinding = -1
    private var drawFramebufferBinding = -1
    private var readFramebufferBinding = -1

    private var activeVao = -1
    private var activeEbo = -1

    private var depthState = false
    private var depthClear = 0.0
    private var depthMode = -1
    private var depthMask = false
    private var blendState = false
    private var blendSrcRgb = -1
    private var blendDstRgb = -1
    private var blendSrcAlpha = -1
    private var blendDstAlpha = -1
    private var cullState = false
    private var cullMode = -1

    fun setupState() {
        activeTexture = GlStateManager._getActiveTexture()
        bindingTexture = GL30.glGetInteger(GL30.GL_TEXTURE_BINDING_2D)
        framebufferBinding = GL30.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING)
        drawFramebufferBinding = GL30.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)
        readFramebufferBinding = GL30.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING)
        activeVao = GL30.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING)
        activeEbo = GL30.glGetInteger(GL30.GL_ELEMENT_ARRAY_BUFFER_BINDING)
        depthState = GL30.glIsEnabled(GL30.GL_DEPTH_TEST)
        depthMode = GL30.glGetInteger(GL30.GL_DEPTH_FUNC)
        depthMask = GL30.glGetBoolean(GL30.GL_DEPTH_WRITEMASK)
        depthClear = GL30.glGetDouble(GL30.GL_DEPTH_CLEAR_VALUE)
        blendState = GL30.glIsEnabled(GL30.GL_BLEND)
        blendSrcRgb = GL30.glGetInteger(GL30.GL_BLEND_SRC_RGB)
        blendDstRgb = GL30.glGetInteger(GL30.GL_BLEND_DST_RGB)
        blendSrcAlpha = GL30.glGetInteger(GL30.GL_BLEND_SRC_ALPHA)
        blendDstAlpha = GL30.glGetInteger(GL30.GL_BLEND_DST_ALPHA)
        cullState = GL30.glIsEnabled(GL30.GL_CULL_FACE)
        cullMode = GL30.glGetInteger(GL30.GL_CULL_FACE_MODE)

        MCGlApi.depthMask(KoolHooks.getGlStateIsWriteDepth())
        if (KoolHooks.getGlStateDepthTest() == DepthCompareOp.ALWAYS) {
            MCGlApi.disable(MCGlApi.DEPTH_TEST)
        } else {
            MCGlApi.enable(MCGlApi.DEPTH_TEST)
            KoolHooks.getGlStateDepthTest()?.glOp(MCGlApi)?.let(MCGlApi::depthFunc)
        }
        when (KoolHooks.getGlStateCullMethod()) {
            CullMethod.CULL_BACK_FACES -> {
                MCGlApi.enable(MCGlApi.CULL_FACE)
                MCGlApi.cullFace(MCGlApi.BACK)
            }

            CullMethod.CULL_FRONT_FACES -> {
                MCGlApi.enable(MCGlApi.CULL_FACE)
                MCGlApi.cullFace(MCGlApi.FRONT)
            }

            else -> MCGlApi.disable(MCGlApi.CULL_FACE)
        }
        KoolHooks.resetShaders(KoolManager.context)
    }

    fun restoreState() {
        GL30.glActiveTexture(activeTexture)
        GL33.glBindTexture(GL33.GL_TEXTURE_2D, bindingTexture)
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebufferBinding)
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFramebufferBinding)
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebufferBinding)
        GL30.glBindVertexArray(activeVao)
        GL30.glBindBuffer(GL30.GL_ELEMENT_ARRAY_BUFFER, activeEbo)
        if (depthState) {
            MCGlApi.enable(MCGlApi.DEPTH_TEST)
            GL30.glDepthFunc(depthMode)
        } else {
            GL30.glDepthFunc(depthMode)
            MCGlApi.disable(MCGlApi.DEPTH_TEST)
        }
        GL30.glDepthMask(depthMask)
        GL30.glBlendFuncSeparate(blendSrcRgb, blendDstRgb, blendSrcAlpha, blendDstAlpha)
        if (blendState) {
            MCGlApi.enable(MCGlApi.BLEND)
        } else {
            MCGlApi.disable(MCGlApi.BLEND)
        }
        if (cullState) {
            MCGlApi.enable(MCGlApi.CULL_FACE)
            GL30.glCullFace(cullMode)
        } else {
            GL30.glCullFace(cullMode)
            MCGlApi.disable(MCGlApi.CULL_FACE)
        }
        RenderSystem.clearDepth(depthClear)
    }

    inline fun <T> withState(block: () -> T): T {
        setupState()
        return try {
            block()
        } finally {
            restoreState()
        }
    }
}

@SubscribeEvent
fun RenderTickEvent.Pre.handle(): Unit = runBlocking {
    UiScale.uiScale.set(1f)
    KoolManager.context.renderFrame()
}

fun Scene.render(recordState: Boolean = true) {
    if (recordState) GlContext.setupState()
    assert(this in KoolManager.context.scenes) { "Scene ${this.name} must be added in KoolManager.context" }
    KoolManager.context.backend.renderScene(this)
    if (recordState) GlContext.restoreState()
}
