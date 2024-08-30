package ru.hollowhorizon.hollowengine.client.gui

import com.mojang.blaze3d.Blaze3D
import com.mojang.blaze3d.vertex.PoseStack
import imgui.internal.ImGui
//? if >=1.20.1
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import ru.hollowhorizon.hc.client.imgui.ImGuiHandler
import ru.hollowhorizon.hc.client.imgui.Graphics
import ru.hollowhorizon.hc.client.utils.math.Interpolation
import ru.hollowhorizon.hc.client.utils.mcText

abstract class ImGuiScreen : Screen("".mcText) {
    private var fadeTime = 0.0
    override fun init() {
        super.init()
        fadeTime = Blaze3D.getTime()
    }

    override fun render(
        //? if >=1.20.1 {
        guiGraphics: GuiGraphics,
        //?} else {
        /*stack: PoseStack,
        *///?}
        mouseX: Int, mouseY: Int, partialTick: Float) {
        //? if >=1.21 {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick)
        //?} elif >=1.20.1 {
        /*renderBackground(guiGraphics)
        *///?} else {
        /*renderBackground(stack)
        *///?}
        val alpha = (Blaze3D.getTime() - fadeTime).toFloat().coerceAtMost(1f)
        ImGui.getStyle().alpha = Interpolation.EXPO_OUT(alpha)
        ImGuiHandler.drawFrame { draw() }

    }

    abstract fun Graphics.draw()

    override fun isPauseScreen() = false

}