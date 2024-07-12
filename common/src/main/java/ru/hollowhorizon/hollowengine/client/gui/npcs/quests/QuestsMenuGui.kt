package ru.hollowhorizon.hollowengine.client.gui.npcs.quests

import com.mojang.blaze3d.Blaze3D
import imgui.ImGui.*
import imgui.extension.nodeditor.NodeEditor
import imgui.extension.nodeditor.NodeEditorConfig
import imgui.extension.nodeditor.NodeEditorContext
import imgui.extension.nodeditor.flag.NodeEditorStyleColor
import imgui.extension.nodeditor.flag.NodeEditorStyleVar
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiStyleVar
import imgui.internal.ImGui
import imgui.type.ImString
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.locale.Language
import net.minecraft.util.Mth
import ru.hollowhorizon.hc.client.imgui.ImGuiHandler
import ru.hollowhorizon.hc.client.imgui.ImGuiMethods
import ru.hollowhorizon.hc.client.utils.*
import ru.hollowhorizon.hollowengine.common.entities.NPCEntity
import ru.hollowhorizon.hollowengine.common.npcs.nodes.itemPicker
import ru.hollowhorizon.hollowengine.common.npcs.quests.QuestConnection
import ru.hollowhorizon.hollowengine.common.npcs.quests.QuestGraph
import ru.hollowhorizon.hollowengine.common.npcs.quests.QuestNode
import ru.hollowhorizon.hollowengine.common.npcs.quests.QuestsCapability
import kotlin.math.pow
import kotlin.math.sqrt

class QuestsMenuGui(val npc: NPCEntity, val editMode: Boolean = true) : Screen("".mcText) {
    val graph: QuestGraph = npc[QuestsCapability::class].questGraph
    private val context = NodeEditorContext(NodeEditorConfig().apply {
        settingsFile = "npc_quests.json"
    })
    private val titleBuffer = ImString(100)
    private val subtitleBuffer = ImString(250)
    var lastModalPopup = -1

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick)
        ImGui.getStyle().setColor(ImGuiCol.ModalWindowDimBg, 0f, 0f, 0f, 0.45f)
        ImGuiHandler.drawFrame {
            pushStyleVar(ImGuiStyleVar.WindowBorderSize, 0f)

            val window = Minecraft.getInstance().window
            setNextWindowPos(0f, 0f)
            setNextWindowSize(window.width.toFloat(), window.height.toFloat())

            centredWindow {
                QuestRenderer.tooltip = {}
                drawQuestGraph()
                QuestRenderer.tooltip()
            }
            popStyleVar()
        }
    }

    private fun drawQuestGraph() {
        val languageManager = Language.getInstance()

        NodeEditor.setCurrentEditor(context)
        NodeEditor.pushStyleVar(NodeEditorStyleVar.NodeRounding, 0f)
        NodeEditor.pushStyleVar(NodeEditorStyleVar.NodeBorderWidth, 0f)
        NodeEditor.pushStyleVar(NodeEditorStyleVar.NodePadding, 0f, 0f, 0f, 0f)
        NodeEditor.pushStyleColor(NodeEditorStyleColor.NodeBg, 0f, 0f, 0f, 0f)
        NodeEditor.pushStyleColor(NodeEditorStyleColor.Bg, 0f, 0f, 0f, 0f)
        NodeEditor.begin("NodeEditor")

        graph.connections.removeIf { (graph.nodes.indexOf(it.input) == -1) or (graph.nodes.indexOf(it.output) == -1) }

        graph.connections.forEach {
            val inputId = (graph.nodes.indexOf(it.input) + 1).toLong()
            val outputId = (graph.nodes.indexOf(it.output) + 1).toLong()
            val inputPosX = NodeEditor.getNodePositionX(inputId) + NodeEditor.getNodeSizeX(inputId) / 2
            val inputPosY = NodeEditor.getNodePositionY(inputId) + NodeEditor.getNodeSizeY(inputId) / 2
            val outputPosX = NodeEditor.getNodePositionX(outputId) + NodeEditor.getNodeSizeX(outputId) / 2
            val outputPosY = NodeEditor.getNodePositionY(outputId) + NodeEditor.getNodeSizeY(outputId) / 2

            val rotation =
                Mth.atan2((outputPosX - inputPosX).toDouble(), (outputPosY - inputPosY).toDouble()).toFloat()

            val length = sqrt((outputPosX - inputPosX).pow(2) + (outputPosY - inputPosY).pow(2))

            val animation = -(Blaze3D.getTime().toFloat() % 6f) / 6f

            ImGui.getWindowDrawList().addImageQuad(
                "hollowengine:textures/gui/quests/quest_line.png".rl.toTexture().id,
                inputPosX + 20 * Mth.cos(rotation),
                inputPosY - 20 * Mth.sin(rotation),
                outputPosX + 20 * Mth.cos(rotation),
                outputPosY - 20 * Mth.sin(rotation),
                outputPosX - 20 * Mth.cos(rotation),
                outputPosY + 20 * Mth.sin(rotation),
                inputPosX - 20 * Mth.cos(rotation),
                inputPosY + 20 * Mth.sin(rotation),
                animation,
                0f,
                length / 220 + animation,
                0f,
                length / 220 + animation,
                1f,
                animation,
                1f,
                colorConvertFloat4ToU32(
                    it.input.color[0],
                    it.input.color[1],
                    it.input.color[2],
                    it.input.color[3]
                ),
            )
        }

        graph.nodes.forEachIndexed { index, questNode ->
            val id = (index + 1).toLong()
            NodeEditor.setNodePosition(id, questNode.pos[0], questNode.pos[1])

            pushID(id)

            NodeEditor.beginNode(id)

            if (QuestRenderer.drawPreview(questNode, editMode)) {
                QuestAcceptScreen(npc, questNode, editMode).open()
            }

            NodeEditor.endNode()

            if (editMode) {
                val nodeX = NodeEditor.getNodePositionX(id)
                val nodeY = NodeEditor.getNodePositionY(id)
                questNode.pos[0] = nodeX
                questNode.pos[1] = nodeY
            }

            popID()
        }


        NodeEditor.suspend()

        drawQuestEditorPopups()
        NodeEditor.resume()

        NodeEditor.end()
        NodeEditor.popStyleVar(3)
        NodeEditor.popStyleColor(2)
    }

    private fun drawQuestEditorPopups() {
        val style = getStyle()
        val rounding = style.popupRounding
        val border = style.popupBorderSize
        style.popupRounding = 10f
        style.popupBorderSize = 2f

        pushStyleColor(ImGuiCol.PopupBg, colorConvertFloat4ToU32(0f, 0f, 0f, 0.6f))

        val selectedNodes = LongArray(NodeEditor.getSelectedObjectCount())
        NodeEditor.getSelectedNodes(selectedNodes, selectedNodes.size)
        val nodeWithContextMenu = NodeEditor.getNodeWithContextMenu()
        val linkWithContextMenu = NodeEditor.getLinkWithContextMenu()
        when {
            nodeWithContextMenu > 0 -> {
                if (selectedNodes.isEmpty() || (selectedNodes.size == 1 && selectedNodes[0] == nodeWithContextMenu)) {
                    ImGui.openPopup("node_editor")
                } else {
                    ImGui.openPopup("node_link_editor")
                }
                ImGui.getStateStorage().setInt(ImGui.getID("node_id"), nodeWithContextMenu.toInt() - 1)
            }

            linkWithContextMenu != -1L -> {
                ImGui.openPopup("link_editor")
                ImGui.getStateStorage().setInt(ImGui.getID("link_id"), linkWithContextMenu.toInt())
            }

            NodeEditor.showBackgroundContextMenu() -> {
                ImGui.openPopup("quests_editor")
            }
        }


        if (isPopupOpen("node_editor")) {
            val nodeId = ImGui.getStateStorage().getInt(ImGui.getID("node_id"))
            if (beginPopup("node_editor")) {
                if (menuItem("Настроить")) {
                    lastModalPopup = nodeId
                }

                if (beginMenu("Установить иконку")) {
                    Minecraft.getInstance().player!!.inventory.itemPicker {
                        val quest = graph.nodes[nodeId]
                        quest.icon = it
                        closeCurrentPopup()
                    }
                    endMenu()
                }
                separator()
                if (beginMenu("Изменить цвет")) {
                    colorPicker4("Цвет", graph.nodes[nodeId].color)
                    endMenu()
                }
                separator()
                if (beginMenu("Переименовать")) {
                    titleBuffer.set(graph.nodes[nodeId].title)
                    ImGuiMethods.textShadow("Название: "); sameLine()

                    ImGui.pushStyleVar(ImGuiStyleVar.FrameBorderSize, 2f)
                    ImGui.pushStyleVar(ImGuiStyleVar.FrameRounding, 5f)
                    inputText("##Title", titleBuffer)
                    ImGui.popStyleVar(2)

                    graph.nodes[nodeId].title = titleBuffer.get()
                    endMenu()
                }

                separator()
                if (beginMenu("Изменить подсказку")) {
                    subtitleBuffer.set(graph.nodes[nodeId].subtitle)
                    ImGuiMethods.textShadow("Подсказка: "); sameLine()

                    ImGui.pushStyleVar(ImGuiStyleVar.FrameBorderSize, 2f)
                    ImGui.pushStyleVar(ImGuiStyleVar.FrameRounding, 5f)
                    inputTextMultiline("##Subtitle", subtitleBuffer)
                    ImGui.popStyleVar(2)
                    graph.nodes[nodeId].subtitle = subtitleBuffer.get()
                    endMenu()
                }

                separator()
                if (menuItem("Изменить рамку")) {
                    TODO("Это пока не реализовано!")
                    closeCurrentPopup()
                }

                separator()
                if (menuItem("Удалить квест")) {
                    graph.nodes.removeAt(nodeId)
                    closeCurrentPopup()
                }

                endPopup()
            }
        }

        if (isPopupOpen("node_link_editor")) {
            val nodeId = ImGui.getStateStorage().getInt(ImGui.getID("node_id"))
            if (beginPopup("node_link_editor")) {
                if (menuItem("Соединить")) {
                    val output = graph.nodes[nodeId]
                    selectedNodes.forEach {
                        val input = graph.nodes[it.toInt() - 1]
                        graph.connections.add(QuestConnection(input, output))
                    }
                }
                endPopup()
            }
        }

        if (isPopupOpen("quests_editor") && beginPopup("quests_editor")) {
            if (selectable("Создать квест")) {
                val node = QuestNode()
                graph.nodes.add(node)

                val canvasX = NodeEditor.toCanvasX(ImGui.getMousePosX()) - 80f
                val canvasY = NodeEditor.toCanvasY(ImGui.getMousePosY()) - 80f
                NodeEditor.setNodePosition(
                    graph.nodes.size.toLong(),
                    canvasX,
                    canvasY
                )
                node.pos[0] = canvasX
                node.pos[1] = canvasY


                closeCurrentPopup()
            }

            endPopup()
        }

        popStyleColor()

        style.popupRounding = rounding
        style.popupBorderSize = border
    }

    override fun onClose() {
        super.onClose()
        npc[QuestsCapability::class].questGraph = graph
    }
}