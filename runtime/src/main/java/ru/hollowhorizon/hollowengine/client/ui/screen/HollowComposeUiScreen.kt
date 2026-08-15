package ru.hollowhorizon.hollowengine.client.ui.screen

import androidx.compose.runtime.Composable
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import ru.hollowhorizon.hollowengine.client.ui.*
import ru.hollowhorizon.hollowengine.client.ui.render.MinecraftUiRenderer
import ru.hollowhorizon.hollowengine.client.ui.render.UiRenderTarget
import ru.hollowhorizon.hollowengine.client.ui.style.CompiledHss
import ru.hollowhorizon.hollowengine.client.utils.mc
import ru.hollowhorizon.hollowengine.client.utils.popPose
import ru.hollowhorizon.hollowengine.client.utils.pushPose
import ru.hollowhorizon.hollowengine.common.ui.UiGuiScale
import ru.hollowhorizon.hollowengine.common.utils.literal
import kotlin.math.ceil


abstract class HollowComposeUiScreen(
    title: String,
    stylesheet: CompiledHss,
) : Screen(title.literal) {
    private val surface = HollowUiSurface(stylesheet = stylesheet)
    private val renderer = MinecraftUiRenderer()
    private val pipeline = PipelinedUiFrameBuilder()

    @Composable
    protected abstract fun Content()

    protected open fun rebuildEveryFrame(): Boolean = false

    /** The scale this screen lays itself out at; [UiGuiScale.Inherit] follows the player's setting. */
    protected open fun guiScale(): UiGuiScale = UiGuiScale.Inherit

    /**
     * Opt-in frame pipelining: the next frame's build (recomposition, style resolve, layout) runs on
     * a background thread while the game renders, and is consumed one frame later.
     */
    protected open fun pipelineFrames(): Boolean = false

    /**
     * Draws on top of the finished UI frame with a vanilla [GuiGraphics].
     *
     * The engine's own screen-render events are posted from a mixin on `Screen.render`, which this class
     * overrides without calling through, so they never fire here. Content that needs vanilla drawing after
     * the frame (item tooltips, for one) hooks in from this method instead.
     */
    protected open fun renderAfterUi(graphics: GuiGraphics, mouseX: Int, mouseY: Int) = Unit

    override fun init() {
        // init() also runs on window resize while a build may be in flight.
        pipeline.reset()
        surface.setContent { Content() }
    }

    /** The surface's own logical size, and how it relates to vanilla's GUI pixels. */
    private class SurfaceScale(val width: Float, val height: Float, val ratio: Float)

    private fun surfaceScale(): SurfaceScale? {
        val window = mc.window
        val factor = when (val scale = guiScale()) {
            UiGuiScale.Inherit -> return null
            UiGuiScale.Auto -> window.calculateScale(0, mc.isEnforceUnicode)
            is UiGuiScale.Fixed -> window.calculateScale(scale.factor, mc.isEnforceUnicode)
        }.coerceAtLeast(1)
        if (factor.toDouble() == window.guiScale) return null

        val logicalWidth = ceil(window.width.toDouble() / factor).toFloat().coerceAtLeast(1f)
        val logicalHeight = ceil(window.height.toDouble() / factor).toFloat().coerceAtLeast(1f)
        val vanillaWidth = window.guiScaledWidth.toFloat()
        if (vanillaWidth <= 0f) return null
        return SurfaceScale(logicalWidth, logicalHeight, logicalWidth / vanillaWidth)
    }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        val scale = surfaceScale()
        val frameWidth = scale?.width ?: width.toFloat()
        val frameHeight = scale?.height ?: height.toFloat()
        val ratio = scale?.ratio ?: 1f
        val pointerX = mouseX.toFloat() * ratio
        val pointerY = mouseY.toFloat() * ratio
        val frame = (if (pipelineFrames()) pipeline.take(frameWidth, frameHeight) else null)
            ?: buildFrame(frameWidth, frameHeight, pointerX, pointerY, System.nanoTime())
        renderScaled(frame, scale)
        renderAfterUi(graphics, mouseX, mouseY)
        UiCursorManager.claim(mc.window.window, this, surface.runtime.cursor, UiCursorManager.ScreenPriority)
        if (pipelineFrames()) {
            pipeline.schedule(frameWidth, frameHeight) {
                buildFrame(frameWidth, frameHeight, pointerX, pointerY, System.nanoTime())
            }
        }
    }

    /**
     * A screen at its own scale draws through a render target whose logical size is the surface's,
     * which is what re-maps the projection.
     */
    private fun renderScaled(frame: HollowUiFrame, scale: SurfaceScale?) {
        if (scale == null) {
            renderer.render(frame)
            return
        }

        val window = mc.window
        val target = UiRenderTarget(
            framebufferId = mc.mainRenderTarget.frameBufferId,
            x = 0,
            y = 0,
            width = window.width,
            height = window.height,
            logicalWidth = scale.width,
            logicalHeight = scale.height,
            scale = window.width / scale.width,
        )

        val projection = RenderSystem.getProjectionMatrix()
        val sorting = RenderSystem.getVertexSorting()
        RenderSystem.getModelViewStack().pushPose()
        try {
            renderer.render(frame, target)
        } finally {
            RenderSystem.setProjectionMatrix(projection, sorting)
            RenderSystem.getModelViewStack().popPose()
            RenderSystem.applyModelViewMatrix()
            mc.mainRenderTarget.bindWrite(true)
        }
    }

    private fun buildFrame(width: Float, height: Float, mouseX: Float, mouseY: Float, nowNanos: Long): HollowUiFrame {
        if (rebuildEveryFrame()) surface.advanceFrameTime(nowNanos)
        return surface.frame(width, height, mouseX, mouseY, nowNanos)
    }

    override fun removed() {
        pipeline.reset()
        renderer.close()
        surface.close()
        super.removed()
    }

    /** Vanilla hands pointer positions in its own GUI pixels; the surface thinks in its own. */
    private fun Double.toSurface(): Float = (this * (surfaceScale()?.ratio ?: 1f)).toFloat()

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        pipeline.await()
        return surface.runtime.mouseClicked(mouseX.toSurface(), mouseY.toSurface(), button, currentUiKeyModifiers())
    }

    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        pipeline.await()
        return surface.runtime.mouseReleased(mouseX.toSurface(), mouseY.toSurface(), button, currentUiKeyModifiers())
    }

    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, dragX: Double, dragY: Double): Boolean {
        pipeline.await()
        return surface.runtime.mouseDragged(
            mouseX.toSurface(), mouseY.toSurface(), button, dragX.toSurface(), dragY.toSurface(),
            currentUiKeyModifiers(),
        )
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        pipeline.await()
        return surface.runtime.mouseScrolled(
            mouseX.toSurface(),
            mouseY.toSurface(),
            scrollX.toFloat(),
            scrollY.toFloat(),
            currentUiKeyModifiers(),
        )
    }

    override fun charTyped(codePoint: Char, modifiers: Int): Boolean {
        if (super.charTyped(codePoint, modifiers)) return true
        pipeline.await()
        return surface.runtime.charTyped(codePoint, modifiers)
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (super.keyPressed(keyCode, scanCode, modifiers)) return true
        pipeline.await()
        return surface.runtime.keyPressed(keyCode, scanCode, modifiers)
    }

    override fun isPauseScreen(): Boolean = false
}
