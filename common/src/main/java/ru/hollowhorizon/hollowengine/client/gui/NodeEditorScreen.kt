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
import ru.hollowhorizon.hc.client.imgui.ImguiHandler
import ru.hollowhorizon.hc.client.utils.rl
import ru.hollowhorizon.hc.client.utils.toTexture
import kotlin.math.max


class NodeEditorScreen : Screen(Component.empty()) {
    private val context = NodeEditorContext(NodeEditorConfig().apply { settingsFile = "node_editor.json" })
    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick)
        ImguiHandler.drawFrame {
            NodeEditor.setCurrentEditor(context)
            NodeEditor.begin("NodeEditor")
            drawNode(1)

            if(NodeEditor.beginCreate()) {}
            NodeEditor.endCreate()

            NodeEditor.end()
        }
    }

    private fun drawNode(id: Int) {
        NodeEditor.pushStyleVar(NodeEditorStyleVar.NodePadding, 8f, 0f, 8f, 8f)
        NodeEditor.pushStyleColor(NodeEditorStyleColor.PinRect, 1f, 1f, 1f, .05f)
        NodeEditor.pushStyleColor(NodeEditorStyleColor.NodeBg, 0f, 0f, 0f, .4f)
        NodeEditor.pushStyleColor(NodeEditorStyleColor.LinkSelRect, 0f, 0f, 0f, 0f)
        NodeEditor.beginNode(id.toLong())
        text("Начало")
        val headerMin = getItemRectMin()
        val headerMaxY = getItemRectMax().y

        val headerMaxO = ImVec2(getItemRectMax().x, headerMaxY)

        NodeEditor.beginPin(101, NodeEditorPinKind.Input)
        var cursor = ImGui.getCursorPos()
        var hovered = ImGui.isMouseHoveringRect(cursor.x, cursor.y, cursor.x + 32f, cursor.y + 32f)
        var color = if (hovered) ImGui.colorConvertFloat4ToU32(0.5f, 0f, 0f, 1f)
        else ImGui.colorConvertFloat4ToU32(0.8f, 0f, 0f, 1f)
        ImGui.getWindowDrawList().addCircle(
            cursor.x + 16f, cursor.y + 16f, 12f, color, 64
        )
        ImGui.dummy(32f, 32f)

        NodeEditor.pinPivotAlignment(.1f, .5f)
        NodeEditor.endPin()
        sameLine()
        text("Вход")
        ImGui.sameLine()
        ImGui.dummy(16f, 0f)
        ImGui.sameLine()
        text("Выход")
        sameLine()
        NodeEditor.beginPin(102, NodeEditorPinKind.Output)
        cursor = ImGui.getCursorPos()
        hovered = ImGui.isMouseHoveringRect(cursor.x, cursor.y, cursor.x + 32f, cursor.y + 32f)
        color = if (hovered) ImGui.colorConvertFloat4ToU32(0.5f, 0f, 0f, 1f)
        else ImGui.colorConvertFloat4ToU32(0.8f, 0f, 0f, 1f)
        ImGui.getWindowDrawList().addCircleFilled(
            cursor.x + 16f, cursor.y + 16f, 12f, ImGui.colorConvertFloat4ToU32(if(hovered) 0.25f else 0.5f, 0f, 0f, 1f), 64
        )
        ImGui.getWindowDrawList().addCircle(
            cursor.x + 16f, cursor.y + 16f, 12f, color, 64
        )
        ImGui.dummy(32f, 32f)

        NodeEditor.pinPivotAlignment(.5f, .5f)
        NodeEditor.endPin()

        val headerMax = ImVec2(max(headerMaxO.x, getItemRectMax().x), headerMaxY)
        NodeEditor.endNode()

        if (isItemVisible()) {
            val nodeRect = NodeEditor.getStyle().getColor(NodeEditorStyleColor.NodeBorder)
            val alpha = (getStyle().alpha * 255).toInt()

            val drawList = NodeEditor.getNodeBackgroundDrawList(id.toLong())
            val halfBorderWidth = NodeEditor.getStyle().nodeBorderWidth * 0.5f

            val animation = Blaze3D.getTime().toFloat() / 50f
            val uvX: Float = (headerMax.x - headerMin.x) / (4.0f * 64f)
            val uvY: Float = (headerMax.y - headerMin.y) / (4.0f * 64f)

            if ((headerMax.x > headerMin.x) && (headerMax.y > headerMin.y)) {
                drawList.addImageRounded(
                    "hollowengine:textures/gui/icons/blueprint_background.png".rl.toTexture().id,
                    headerMin.x - (8 - halfBorderWidth),
                    headerMin.y +halfBorderWidth,
                    headerMax.x + (8 - halfBorderWidth),
                    headerMax.y + (0),
                    animation,
                    0f,
                    uvX + animation,
                    uvY,
                    ImGui.colorConvertFloat4ToU32(0.8f, 0f, 0f, 1f),
                    NodeEditor.getStyle().nodeRounding,
                    ImDrawFlags.RoundCornersTop
                )
            }

            val headerSeparatorMin = ImVec2(headerMin.x, headerMin.y)
            val headerSeparatorMax = ImVec2(headerMax.x, headerMax.y)

            if ((headerSeparatorMax.x > headerSeparatorMin.x) && (headerSeparatorMax.y > headerSeparatorMin.y)) {
                drawList.addLine(
                    headerMin.x - 6.5f - halfBorderWidth,
                    headerMax.y,
                    headerMax.x + (7 - halfBorderWidth),
                    headerMax.y,
                    ImGui.colorConvertFloat4ToU32(nodeRect.x, nodeRect.y, nodeRect.y, nodeRect.w),
                    1f
                )
            }
        }
        NodeEditor.popStyleVar(1)
        NodeEditor.popStyleColor(3)
    }
}