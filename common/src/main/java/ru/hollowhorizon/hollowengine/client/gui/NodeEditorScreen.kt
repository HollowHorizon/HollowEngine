package ru.hollowhorizon.hollowengine.client.gui

import com.mojang.blaze3d.Blaze3D
import imgui.ImGui
import imgui.ImGui.isItemVisible
import imgui.ImVec2
import imgui.extension.nodeditor.NodeEditor
import imgui.extension.nodeditor.NodeEditorConfig
import imgui.extension.nodeditor.NodeEditorContext
import imgui.extension.nodeditor.flag.NodeEditorPinKind
import imgui.extension.nodeditor.flag.NodeEditorStyleColor
import imgui.extension.nodeditor.flag.NodeEditorStyleVar
import imgui.flag.ImDrawFlags
import imgui.internal.ImGui.*
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import ru.hollowhorizon.hc.client.imgui.FontAwesomeIcons
import ru.hollowhorizon.hc.client.imgui.ImGuiHandler
import ru.hollowhorizon.hc.client.utils.rl
import ru.hollowhorizon.hc.client.utils.toTexture
import kotlin.math.max


class NodeEditorScreen : Screen(Component.empty()) {

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick)
        ImGuiHandler.drawFrame {

        }
    }

    private fun drawNode(id: Int) {

    }
}