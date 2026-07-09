package ru.hollowhorizon.hollowengine.client.ui.screen

import androidx.compose.runtime.Composable
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import ru.hollowhorizon.hollowengine.client.ui.HollowUiSurface
import ru.hollowhorizon.hollowengine.client.ui.UiCursorManager
import ru.hollowhorizon.hollowengine.client.ui.currentUiKeyModifiers
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

    @Composable
    protected abstract fun Content()

    protected open fun rebuildEveryFrame(): Boolean = false

    override fun init() {
        surface.setContent { Content() }
    }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        val nowMillis = System.nanoTime()
        if (rebuildEveryFrame()) surface.advanceFrameTime(nowMillis)
        val frame = surface.frame(width.toFloat(), height.toFloat(), mouseX.toFloat(), mouseY.toFloat(), nowMillis)
        renderer.render(frame)
        UiCursorManager.apply(mc.window.window, surface.runtime.cursor)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean =
        surface.runtime.mouseClicked(mouseX.toFloat(), mouseY.toFloat(), button, currentUiKeyModifiers())

    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean =
        surface.runtime.mouseReleased(mouseX.toFloat(), mouseY.toFloat(), button, currentUiKeyModifiers())

    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, dragX: Double, dragY: Double): Boolean =
        surface.runtime.mouseDragged(mouseX.toFloat(), mouseY.toFloat(), button, dragX.toFloat(), dragY.toFloat())

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean =
        surface.runtime.mouseScrolled(mouseX.toFloat(), mouseY.toFloat(), scrollX.toFloat(), scrollY.toFloat())

    override fun charTyped(codePoint: Char, modifiers: Int): Boolean {
        if (super.charTyped(codePoint, modifiers)) return true
        return surface.runtime.charTyped(codePoint, modifiers)
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (super.keyPressed(keyCode, scanCode, modifiers)) return true
        return surface.runtime.keyPressed(keyCode, scanCode, modifiers)
    }

    override fun isPauseScreen(): Boolean = false
}