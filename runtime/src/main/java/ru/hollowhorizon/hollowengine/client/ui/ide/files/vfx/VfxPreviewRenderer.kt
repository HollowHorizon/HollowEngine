package ru.hollowhorizon.hollowengine.client.ui.ide.files.vfx

import com.mojang.blaze3d.pipeline.TextureTarget
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.BufferUploader
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.Tesselator
import com.mojang.blaze3d.vertex.VertexFormat
import com.mojang.blaze3d.vertex.VertexSorting
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.GameRenderer
import org.joml.Matrix4f
import org.joml.Vector3f
import org.joml.Vector4f
import org.lwjgl.opengl.GL33
import ru.hollowhorizon.hollowengine.client.render.DebugLines
import ru.hollowhorizon.hollowengine.client.ui.ide.files.animator.AnimatorColors
import ru.hollowhorizon.hollowengine.client.ui.layout.UiRect
import ru.hollowhorizon.hollowengine.client.ui.render.disableScissor
import ru.hollowhorizon.hollowengine.client.ui.render.uiBlendWritesAlpha
import ru.hollowhorizon.hollowengine.client.ui.render.uiWriteAlpha
import ru.hollowhorizon.hollowengine.client.vfx.VfxInstance
import ru.hollowhorizon.hollowengine.client.vfx.render.VfxDrawList
import ru.hollowhorizon.hollowengine.client.vfx.render.VfxFrameRenderer
import ru.hollowhorizon.hollowengine.client.vfx.render.VfxPostProcessor
import ru.hollowhorizon.hollowengine.client.vfx.render.VfxView
import ru.hollowhorizon.hollowengine.common.utils.math.Vec3f
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Draws the preview into a target of its own, as large as the panel is in real pixels, and puts
 * that on the panel.
 *
 * A target of its own is what lets the preview show what the world shows: full-screen passes run
 * over the preview alone, and a material that reads the scene depth reads the preview's.
 */
internal class VfxPreviewRenderer {
    private var target: TextureTarget? = null
    private val frame = VfxDrawList()

    /** The camera shake of the last frame, in degrees; the next frame's camera takes it. */
    val shake = FloatArray(3)

    fun render(preview: VfxPreviewState, instance: VfxInstance, rect: UiRect, stack: PoseStack) {
        val size = pixelSize(rect, stack) ?: return
        var texture = 0

        withOwnTarget {
            val offscreen = targetOf(size.first, size.second)
            texture = offscreen.colorTextureId
            offscreen.setClearColor(Background.red, Background.green, Background.blue, 1f)
            offscreen.clear(Minecraft.ON_OSX)
            offscreen.bindWrite(true)

            val view = preview.viewMatrix(shake)
            val projection = preview.perspective(size.first.toFloat(), size.second.toFloat())
            RenderSystem.setProjectionMatrix(projection, VertexSorting.DISTANCE_TO_ORIGIN)
            RenderSystem.getModelViewStack().set(view)
            RenderSystem.applyModelViewMatrix()

            val buffers = Minecraft.getInstance().renderBuffers().bufferSource()
            if (preview.showFloor) {
                drawFloor(DebugLines.batch(buffers, PoseStack(), DebugLines.BOUND))
                buffers.endBatch(DebugLines.BOUND)
            }

            val eye = preview.cameraBasis().eye
            instance.camera.set(eye.x, eye.y, eye.z)
            frame.clear()
            instance.collect(frame, Matrix4f())
            frame.shake.copyInto(shake)

            VfxFrameRenderer.render(
                frame,
                VfxView(
                    modelView = view,
                    projection = projection,
                    right = Vector3f(view.m00(), view.m10(), view.m20()),
                    up = Vector3f(view.m01(), view.m11(), view.m21()),
                    eye = eye,
                ),
                offscreen,
            )
            VfxPostProcessor.apply(frame.posts, offscreen)
        }

        blit(texture, rect, stack)
    }

    /**
     * Runs [draw] with the state the panel was drawing in set aside and put back afterwards.
     */
    private inline fun withOwnTarget(draw: () -> Unit) {
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

    fun close() {
        target?.destroyBuffers()
        target = null
    }

    /** How many pixels of the target being drawn into the panel covers, whatever scale the UI uses. */
    private fun pixelSize(rect: UiRect, stack: PoseStack): Pair<Int, Int>? {
        val toClip = Matrix4f(RenderSystem.getProjectionMatrix()).mul(RenderSystem.getModelViewMatrix())
            .mul(stack.last().pose())
        val from = toClip.transform(Vector4f(0f, 0f, 0f, 1f))
        val to = toClip.transform(Vector4f(rect.width, rect.height, 0f, 1f))
        val viewport = IntArray(4).also { GL33.glGetIntegerv(GL33.GL_VIEWPORT, it) }
        val width = (abs(to.x / to.w - from.x / from.w) * 0.5f * viewport[2]).roundToInt()
        val height = (abs(to.y / to.w - from.y / from.w) * 0.5f * viewport[3]).roundToInt()
        if (width < 2 || height < 2) return null
        return width.coerceAtMost(MAX_SIZE) to height.coerceAtMost(MAX_SIZE)
    }

    private fun targetOf(width: Int, height: Int): TextureTarget {
        val current = target
        if (current != null && current.width == width && current.height == height) return current
        current?.destroyBuffers()
        return TextureTarget(width, height, true, Minecraft.ON_OSX).also { target = it }
    }

    /** The target over the panel, upside down, since a target starts at the bottom. */
    private fun blit(texture: Int, rect: UiRect, stack: PoseStack) {
        RenderSystem.setShader(GameRenderer::getPositionTexShader)
        RenderSystem.setShaderTexture(0, texture)
        RenderSystem.disableBlend()
        RenderSystem.disableCull()
        val pose = stack.last().pose()
        val builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX)
        builder.addVertex(pose, 0f, 0f, 0f).setUv(0f, 1f)
        builder.addVertex(pose, 0f, rect.height, 0f).setUv(0f, 0f)
        builder.addVertex(pose, rect.width, rect.height, 0f).setUv(1f, 0f)
        builder.addVertex(pose, rect.width, 0f, 0f).setUv(1f, 1f)
        BufferUploader.drawWithShader(builder.buildOrThrow())
        RenderSystem.enableCull()
    }

    private fun drawFloor(lines: DebugLines.Batch) {
        val half = FLOOR_HALF_SIZE
        for (step in -half..half) {
            val offset = step.toFloat()
            val color = if (step == 0) AXIS_COLOR else GRID_COLOR
            lines.line(Vec3f(-half.toFloat(), 0f, offset), Vec3f(half.toFloat(), 0f, offset), color)
            lines.line(Vec3f(offset, 0f, -half.toFloat()), Vec3f(offset, 0f, half.toFloat()), color)
        }
    }

    private companion object {
        val Background = AnimatorColors.Canvas
        const val MAX_SIZE = 8192
        const val FLOOR_HALF_SIZE = 6
        const val GRID_COLOR = 0x40AFC4E0
        const val AXIS_COLOR = 0x80DCBF73.toInt()
    }
}
