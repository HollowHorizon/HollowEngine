package ru.hollowhorizon.hollowengine.client.ui.screen

import androidx.compose.runtime.Composable
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import ru.hollowhorizon.hollowengine.client.ui.*
import ru.hollowhorizon.hollowengine.client.ui.render.MinecraftUiRenderer
import ru.hollowhorizon.hollowengine.client.ui.style.CompiledHss
import ru.hollowhorizon.hollowengine.client.utils.mc
import ru.hollowhorizon.hollowengine.common.utils.literal


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

    /**
     * Opt-in frame pipelining: the next frame's build (recomposition, style resolve, layout) runs on
     * a background thread while the game renders, and is consumed one frame later.
     */
    protected open fun pipelineFrames(): Boolean = false

    override fun init() {
        // init() also runs on window resize while a build may be in flight.
        pipeline.reset()
        surface.setContent { Content() }
    }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        val frameWidth = width.toFloat()
        val frameHeight = height.toFloat()
        val frame = (if (pipelineFrames()) pipeline.take(frameWidth, frameHeight) else null)
            ?: buildFrame(frameWidth, frameHeight, mouseX.toFloat(), mouseY.toFloat(), System.nanoTime())
        renderer.render(frame)
        UiCursorManager.apply(mc.window.window, surface.runtime.cursor)
        if (pipelineFrames()) {
            val nextMouseX = mouseX.toFloat()
            val nextMouseY = mouseY.toFloat()
            pipeline.schedule(frameWidth, frameHeight) {
                buildFrame(frameWidth, frameHeight, nextMouseX, nextMouseY, System.nanoTime())
            }
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

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        pipeline.await()
        return surface.runtime.mouseClicked(mouseX.toFloat(), mouseY.toFloat(), button, currentUiKeyModifiers())
    }

    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        pipeline.await()
        return surface.runtime.mouseReleased(mouseX.toFloat(), mouseY.toFloat(), button, currentUiKeyModifiers())
    }

    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, dragX: Double, dragY: Double): Boolean {
        pipeline.await()
        return surface.runtime.mouseDragged(
            mouseX.toFloat(), mouseY.toFloat(), button, dragX.toFloat(), dragY.toFloat(), currentUiKeyModifiers(),
        )
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        pipeline.await()
        return surface.runtime.mouseScrolled(
            mouseX.toFloat(),
            mouseY.toFloat(),
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
