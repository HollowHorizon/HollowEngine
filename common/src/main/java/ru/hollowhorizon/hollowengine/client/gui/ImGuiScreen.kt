package ru.hollowhorizon.hollowengine.client.gui

import com.mojang.blaze3d.Blaze3D
import imgui.internal.ImGui
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import ru.hollowhorizon.hc.client.imgui.ImGuiHandler
import ru.hollowhorizon.hc.client.imgui.ImGuiMethods
import ru.hollowhorizon.hc.client.utils.math.Interpolation
import ru.hollowhorizon.hc.client.utils.mcText

abstract class ImGuiScreen : Screen("".mcText) {
    private var fadeTime = 0.0
    override fun init() {
        super.init()
        fadeTime = Blaze3D.getTime()
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick)
        val alpha = (Blaze3D.getTime() - fadeTime).toFloat().coerceAtMost(1f)
        ImGui.getStyle().alpha = Interpolation.EXPO_OUT(alpha)
        ImGuiHandler.drawFrame { draw() }

    }

    abstract fun ImGuiMethods.draw()

    override fun isPauseScreen() = false

}