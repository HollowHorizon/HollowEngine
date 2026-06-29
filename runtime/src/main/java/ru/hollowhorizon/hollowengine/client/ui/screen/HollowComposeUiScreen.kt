package ru.hollowhorizon.hollowengine.client.ui.screen

import androidx.compose.runtime.*
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import ru.hollowhorizon.hollowengine.client.ui.HollowUiSurface
import ru.hollowhorizon.hollowengine.client.ui.LocalUiFrameTimeNanos
import ru.hollowhorizon.hollowengine.client.ui.hss.CompiledHss
import ru.hollowhorizon.hollowengine.client.ui.render.MinecraftUiRenderer
import ru.hollowhorizon.hollowengine.common.utils.literal


abstract class HollowComposeUiScreen(
    title: String,
    stylesheet: CompiledHss,
) : Screen(title.literal) {
    private val surface = HollowUiSurface(stylesheet = stylesheet)
    private val renderer = MinecraftUiRenderer()
    private var frameTimeNanos by mutableStateOf(0L)

    @Composable
    protected abstract fun Content()

    protected open fun rebuildEveryFrame(): Boolean = false

    override fun init() {
        surface.setContent {
            CompositionLocalProvider(LocalUiFrameTimeNanos provides frameTimeNanos) {
                Content()
            }
        }
    }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        val nowMillis = System.nanoTime()
        if (rebuildEveryFrame()) frameTimeNanos = nowMillis
        val frame = surface.frame(width.toFloat(), height.toFloat(), mouseX.toFloat(), mouseY.toFloat(), nowMillis)
        renderer.render(frame.commands)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean =
        surface.runtime.mouseClicked(mouseX.toFloat(), mouseY.toFloat(), button)

    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean =
        surface.runtime.mouseReleased(mouseX.toFloat(), mouseY.toFloat(), button)

    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, dragX: Double, dragY: Double): Boolean =
        surface.runtime.mouseDragged(mouseX.toFloat(), mouseY.toFloat(), button, dragX.toFloat(), dragY.toFloat())

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean =
        surface.runtime.mouseScrolled(mouseX.toFloat(), mouseY.toFloat(), scrollX.toFloat(), scrollY.toFloat())

    override fun charTyped(codePoint: Char, modifiers: Int): Boolean = surface.runtime.charTyped(codePoint, modifiers)

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean =
        surface.runtime.keyPressed(keyCode, scanCode, modifiers)

    override fun isPauseScreen(): Boolean = false
}