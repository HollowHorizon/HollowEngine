package ru.hollowhorizon.hollowengine.client.gui

import imgui.flag.ImGuiStyleVar
import imgui.flag.ImGuiWindowFlags
import imgui.internal.ImGui
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import ru.hollowhorizon.hc.client.imgui.ImGuiMethods
import ru.hollowhorizon.hc.client.imgui.ImGuiHandler
import ru.hollowhorizon.hc.client.utils.rl

class QuestsGui : Screen(Component.empty()) {
    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick)
        ImGuiHandler.drawFrame {
            ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 0f, 0f)
            ImGui.setNextWindowPos(0f, 0f)
            val window = Minecraft.getInstance().window
            ImGui.setNextWindowSize(window.width.toFloat(), window.height.toFloat())

            ImGuiMethods.centredWindow(
                args = ImGuiWindowFlags.NoMove or ImGuiWindowFlags.NoResize or ImGuiWindowFlags.NoTitleBar or
                        ImGuiWindowFlags.NoBackground
            ) {
                val width = window.width
                val ratio = 0.0828125f
                image("hollowengine:textures/gui/event_list/event_list.png".rl, width.toFloat(), width * ratio)
                val size = ImGui.calcTextSize("Список событий")
                ImGui.setCursorPos(width / 2f - size.x / 2, width * ratio / 2 - size.y / 2)
                text("Список событий")
            }
            ImGui.popStyleVar()
        }
    }
}