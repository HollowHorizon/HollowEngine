package ru.hollowhorizon.hollowengine.client.gui.npcs

import com.mojang.blaze3d.Blaze3D
import imgui.ImVec2
import imgui.ImVec4
import imgui.extension.nodeditor.NodeEditor
import imgui.extension.nodeditor.NodeEditorConfig
import imgui.extension.nodeditor.NodeEditorContext
import imgui.extension.nodeditor.flag.NodeEditorPinKind
import imgui.extension.nodeditor.flag.NodeEditorStyleColor
import imgui.extension.nodeditor.flag.NodeEditorStyleVar
import imgui.flag.ImDrawFlags
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiStyleVar
import imgui.internal.ImGui
import imgui.type.ImLong
import net.minecraft.locale.Language
import ru.hollowhorizon.hc.client.imgui.FontAwesomeIcons
import ru.hollowhorizon.hc.client.utils.rl
import ru.hollowhorizon.hc.client.utils.toTexture
import ru.hollowhorizon.hollowengine.common.npcs.CURRENT_GRAPH
import ru.hollowhorizon.hollowengine.common.npcs.ScriptGraph
import ru.hollowhorizon.hollowengine.common.npcs.connections.Connection
import ru.hollowhorizon.hollowengine.common.npcs.nodes.ScriptNode
import ru.hollowhorizon.hollowengine.common.npcs.nodes.color
import ru.hollowhorizon.hollowengine.common.npcs.nodes.pins.Pin
import ru.hollowhorizon.hollowengine.common.npcs.nodes.pins.Pins
import ru.hollowhorizon.hollowengine.common.npcs.nodes.pins.color
import ru.hollowhorizon.hollowengine.common.npcs.nodes.pins.isConnected
import ru.hollowhorizon.hollowengine.common.registry.NodesRegistry
import kotlin.math.max

object GraphRenderer {
    val a = ImLong()
    val b = ImLong()
    private val context = NodeEditorContext(NodeEditorConfig().apply { settingsFile = "node_editor.json" })

    fun draw(graph: ScriptGraph) {
        val languageManager = Language.getInstance()
        CURRENT_GRAPH = graph
        var nodeId = 1
        val nodeMap = HashMap<ScriptNode, Int>()
        val pinMap = HashMap<Pin<*>, Int>()
        NodeRenderer.pinId = 33000

        NodeEditor.setCurrentEditor(context)
        NodeEditor.begin("NodeEditor")

        NodeEditor.pushStyleVar(NodeEditorStyleVar.NodePadding, 8f, 0f, 8f, 8f)
        NodeEditor.pushStyleColor(NodeEditorStyleColor.PinRect, 1f, 1f, 1f, .05f)
        NodeEditor.pushStyleColor(NodeEditorStyleColor.NodeBg, 0f, 0f, 0f, .4f)
        NodeEditor.pushStyleColor(NodeEditorStyleColor.LinkSelRect, 0f, 0f, 0f, 0f)

        for (node in graph.nodes) {
            val id = nodeId++
            nodeMap[node] = id
            NodeRenderer.draw(pinMap, id, node)
        }

        val pin = pinMap.entries.find { it.value == a.get().toInt() }?.key ?: pinMap.entries.find {
            it.value == b.get().toInt()
        }?.key

        val color = ImVec4(1f, 1f, 1f, 1f)
        if (pin != null) ImGui.colorConvertU32ToFloat4(pin.color, color)
        val thickness = if (pin?.type == Pins.NODE) 5f else 3f

        if (NodeEditor.beginCreate(color.x, color.y, color.z, color.w, thickness)) {

            if (NodeEditor.queryNewLink(a, b, color.x, color.y, color.z, color.w, thickness)) {
                fun connect(): Boolean {
                    val first = a.get().toInt()
                    val second = b.get().toInt()

                    val inputId = pinMap.entries.find { it.value == first }?.key ?: return false
                    val outputId = pinMap.entries.find { it.value == second }?.key ?: return false

                    val inputNode =
                        graph.nodes.find { it.inputs.contains(inputId) || it.outputs.contains(inputId) } ?: return false
                    val outputNode =
                        graph.nodes.find { it.inputs.contains(outputId) || it.outputs.contains(outputId) } ?: return false

                    if (inputId.type != outputId.type) return false // Пины разных типов не будут работать
                    if (inputNode == outputNode) return false // Подключить пин к самому себе нельзя
                    if (inputId.mode == outputId.mode) return false // Входной и выходной пины должны иметь разный типы

                    if (NodeEditor.acceptNewItem(color.x, color.y, color.z, color.w, thickness)) {
                        graph.connections += Connection(
                            inputNode,
                            outputNode,
                            inputId,
                            outputId
                        )
                    }
                    a.set(0)
                    b.set(0)

                    return true
                }
                if(!connect()) NodeEditor.rejectNewItem(1f, 0.25f, 0.25f, 1f, 3f)
            }
        }
        NodeEditor.endCreate()

        var uniqueLinkId = 1
        for (link in graph.connections) {
            val color = ImVec4()
            ImGui.colorConvertU32ToFloat4(link.inputPin.color, color)
            val thickness = if (link.inputPin.type == Pins.NODE) 5f else 3f
            NodeEditor.link(
                uniqueLinkId++.toLong(),
                pinMap[link.inputPin]!!.toLong(),
                pinMap[link.outputPin]!!.toLong(),
                color.x, color.y, color.z, color.w,
                thickness
            )
        }

        NodeEditor.suspend()

        val nodeWithContextMenu = NodeEditor.getNodeWithContextMenu()
        val linkWithContextMenu = NodeEditor.getLinkWithContextMenu()
        when {
            nodeWithContextMenu != -1L -> {
                ImGui.openPopup("node_context")
                ImGui.getStateStorage().setInt(ImGui.getID("delete_node_id"), nodeWithContextMenu.toInt())
            }

            linkWithContextMenu != -1L -> {
                ImGui.openPopup("link_context")
                ImGui.getStateStorage().setInt(ImGui.getID("delete_link_id"), linkWithContextMenu.toInt())
            }

            NodeEditor.showBackgroundContextMenu() -> {
                ImGui.openPopup("node_editor_context")
            }
        }

        if (ImGui.isPopupOpen("node_context")) {
            val targetNode = ImGui.getStateStorage().getInt(ImGui.getID("delete_node_id"))
            val node = nodeMap.entries.find { it.value == targetNode }?.key
            if (node != null && ImGui.beginPopup("node_context")) {
                if (ImGui.button(
                        "Удалить ($targetNode) " + languageManager.getOrDefault(
                            "nodes.${node.type.namespace}.${
                                node.type.path.replace(
                                    '/',
                                    '.'
                                )
                            }"
                        )
                    )
                ) {
                    graph.nodes.remove(node)
                    graph.connections.removeIf { it.output == node || it.input == node }
                    ImGui.closeCurrentPopup()
                }
                ImGui.endPopup()
            }
        }

        if (ImGui.isPopupOpen("link_context")) {
            val targetLink = ImGui.getStateStorage().getInt(ImGui.getID("delete_link_id"))
            val node = nodeMap.entries.find { it.value == targetLink }?.key
            if (node != null && ImGui.beginPopup("link_context")) {
                if (ImGui.button("Удалить соединение")) {
                    graph.connections.removeAt(targetLink - 1)
                    ImGui.closeCurrentPopup()
                }
                ImGui.endPopup()
            }
        }

        val style = ImGui.getStyle()
        val rounding = style.popupRounding
        val border = style.popupBorderSize
        style.popupRounding = 10f
        style.popupBorderSize = 2f

        ImGui.pushStyleColor(ImGuiCol.PopupBg, ImGui.colorConvertFloat4ToU32(0f, 0f, 0f, 0.6f))

        if (ImGui.beginPopup("node_editor_context")) {

            NodesRegistry.entries().keys.toTree().drawMenu()?.let {
                graph.nodes += NodesRegistry.find<ScriptNode>(it)
                val canvasX = NodeEditor.toCanvasX(ImGui.getMousePosX())
                val canvasY = NodeEditor.toCanvasY(ImGui.getMousePosY())
                NodeEditor.setNodePosition(
                    nodeId.toLong(),
                    canvasX,
                    canvasY
                )
                ImGui.closeCurrentPopup()
            }

            ImGui.endPopup()
        }

        ImGui.popStyleColor()

        style.popupRounding = rounding
        style.popupBorderSize = border

        NodeEditor.resume()

        NodeEditor.popStyleVar(1)
        NodeEditor.popStyleColor(3)
        NodeEditor.end()
    }
}

object NodeRenderer {
    var pinId = 33000

    fun draw(pinMap: MutableMap<Pin<*>, Int>, id: Int, node: ScriptNode) {
        val languageManager = Language.getInstance()

        ImGui.pushID(id)
        NodeEditor.beginNode(id.toLong())

        ImGui.text(languageManager.getOrDefault("nodes.${node.type.namespace}.${node.type.path.replace('/', '.')}"))
        val headerMin = ImGui.getItemRectMin()
        val headerMaxY = ImGui.getItemRectMax().y
        ImGui.sameLine()
        ImGui.dummy(20f, 0f)
        ImGui.sameLine()
        val headerSizeX = ImGui.getCursorPosX()
        ImGui.newLine()

        val pos = ImGui.getCursorPos()
        var maxX = pos.x
        var maxY = pos.y

        node.inputs.forEach { pin ->
            val pinId = pinId++
            pinMap[pin] = pinId
            NodeEditor.beginPin(pinId.toLong(), NodeEditorPinKind.Input)
            ImGui.pushID(pinId)
            val cursor = ImGui.getCursorPos()
            val hovered = ImGui.isMouseHoveringRect(cursor.x, cursor.y, cursor.x + 32f, cursor.y + 32f)
            val pinColor = ImVec4().apply { ImGui.colorConvertU32ToFloat4(pin.color, this) }
            val color = if (hovered) imgui.ImGui.colorConvertFloat4ToU32(pinColor.x, pinColor.y, pinColor.z, pinColor.w)
            else ImGui.colorConvertFloat4ToU32(pinColor.x * 0.8f, pinColor.y * 0.8f, pinColor.z * 0.8f, pinColor.w)
            if (pin.isConnected) {
                val fillColor = if (hovered) ImGui.colorConvertFloat4ToU32(
                    pinColor.x * 0.35f,
                    pinColor.y * 0.35f,
                    pinColor.z * 0.35f,
                    pinColor.w
                )
                else ImGui.colorConvertFloat4ToU32(pinColor.x * 0.5f, pinColor.y * 0.5f, pinColor.z * 0.5f, pinColor.w)

                ImGui.getWindowDrawList().addCircleFilled(
                    cursor.x + 16f,
                    cursor.y + 16f,
                    12f,
                    fillColor,
                    64
                )
            }
            ImGui.getWindowDrawList().addCircle(
                cursor.x + 16f, cursor.y + 16f, 12f, color, 64
            )
            ImGui.dummy(32f, 32f)
            NodeEditor.pinPivotAlignment(.1f, .5f)
            NodeEditor.endPin()

            ImGui.sameLine()
            ImGui.text(languageManager.getOrDefault(pin.name))
            if (!pin.isConnected) pin.pick()
            ImGui.sameLine()
            maxX = max(maxX, ImGui.getCursorPosX())
            ImGui.newLine()
            ImGui.popID()
            maxY = max(maxY, ImGui.getCursorPosY())
        }

        ImGui.setCursorPos(maxX + 20, pos.y)

        var largest =
            node.outputs.maxOfOrNull { maxX + 20 + ImGui.calcTextSize(languageManager.getOrDefault(it.name)).x } ?: 0f

        largest = max(largest, headerSizeX - 32f)

        node.outputs.forEach { pin ->
            val pinId = pinId++
            pinMap[pin] = pinId

            val text = languageManager.getOrDefault(pin.name)
            ImGui.setCursorPosX(largest - ImGui.calcTextSize(text).x - 8f)
            ImGui.text(text)
            ImGui.sameLine()
            ImGui.setCursorPosX(largest)

            NodeEditor.beginPin(pinId.toLong(), NodeEditorPinKind.Output)
            ImGui.pushID(pinId)
            val cursor = ImGui.getCursorPos()
            val hovered = ImGui.isMouseHoveringRect(cursor.x, cursor.y, cursor.x + 32f, cursor.y + 32f)
            val pinColor = ImVec4().apply { ImGui.colorConvertU32ToFloat4(pin.color, this) }

            val color = if (hovered) ImGui.colorConvertFloat4ToU32(pinColor.x, pinColor.y, pinColor.z, pinColor.w)
            else ImGui.colorConvertFloat4ToU32(pinColor.x * 0.8f, pinColor.y * 0.8f, pinColor.z * 0.8f, pinColor.w)

            val fillColor = if (hovered) ImGui.colorConvertFloat4ToU32(
                pinColor.x * 0.35f,
                pinColor.y * 0.35f,
                pinColor.z * 0.35f,
                pinColor.w
            )
            else ImGui.colorConvertFloat4ToU32(pinColor.x * 0.5f, pinColor.y * 0.5f, pinColor.z * 0.5f, pinColor.w)

            ImGui.getWindowDrawList().addCircleFilled(
                cursor.x + 16f,
                cursor.y + 16f,
                12f,
                fillColor,
                64
            )
            ImGui.getWindowDrawList().addCircle(
                cursor.x + 16f, cursor.y + 16f, 12f, color, 64
            )
            ImGui.dummy(32f, 32f)

            NodeEditor.pinPivotAlignment(.5f, .5f)
            NodeEditor.endPin()
            ImGui.popID()

            ImGui.setCursorPosX(maxX + 20)
            maxY = max(maxY, ImGui.getCursorPosY())
        }

        ImGui.newLine()

        node.draw(CURRENT_GRAPH)

        NodeEditor.endNode()

        NodeEditor.suspend()
        node.drawPost(CURRENT_GRAPH)
        NodeEditor.resume()

        ImGui.popID()
        val headerMaxO = ImVec2(ImGui.getItemRectMax().x, headerMaxY)

        val headerMax = ImVec2(max(headerMaxO.x, ImGui.getItemRectMax().x), headerMaxY)

        if (imgui.ImGui.isItemVisible()) {
            ImGui.setCursorPos(headerMax.x - ImGui.calcTextSize(FontAwesomeIcons.InfoCircle).x - 8f, headerMin.y)
            ImGui.text(FontAwesomeIcons.InfoCircle)
            if (ImGui.isItemHovered()) {
                NodeEditor.suspend()
                ImGui.beginTooltip()
                val pattern = "nodes.${node.type.namespace}.${node.type.path.replace('/', '.')}.desc"
                val desc = if (languageManager.has(pattern)) languageManager.getOrDefault(pattern) else "Описания нет."
                ImGui.textColored(ImGui.colorConvertFloat4ToU32(1f, 0.84313726f, 0f, 1f), desc)
                ImGui.endTooltip()
                NodeEditor.resume()
            }

            val nodeRect = NodeEditor.getStyle().getColor(NodeEditorStyleColor.NodeBorder)

            val drawList = NodeEditor.getNodeBackgroundDrawList(id.toLong())
            val halfBorderWidth = NodeEditor.getStyle().nodeBorderWidth * 0.5f

            val uvX: Float = (headerMax.x - headerMin.x) / (4.0f * 64f)
            val uvY: Float = (headerMax.y - headerMin.y) / (4.0f * 64f)

            if ((headerMax.x > headerMin.x) && (headerMax.y > headerMin.y)) {
                val nodeColor = ImVec4()
                ImGui.colorConvertU32ToFloat4(node.color, nodeColor)
                val color = ImGui.colorConvertFloat4ToU32(
                    nodeColor.x * 0.8f,
                    nodeColor.y * 0.8f,
                    nodeColor.z * 0.8f,
                    nodeColor.w
                )
                drawList.addImageRounded(
                    "hollowengine:textures/gui/icons/blueprint_background.png".rl.toTexture().id,
                    headerMin.x - (8 - halfBorderWidth),
                    headerMin.y + halfBorderWidth,
                    headerMax.x,
                    headerMax.y + (0),
                    0f,
                    0f,
                    uvX,
                    uvY,
                    color,
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
                    headerMax.x,
                    headerMax.y,
                    imgui.ImGui.colorConvertFloat4ToU32(nodeRect.x, nodeRect.y, nodeRect.y, nodeRect.w),
                    1f
                )
            }
        }
    }
}

fun beginColumnGroup(width: Float) {
    ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, 0.0f, 0.0f)
    ImGui.dummy(width, 0f)
    ImGui.popStyleVar()

    ImGui.columns(2, "##TreeColumns", false)

    ImGui.setColumnWidth(
        0, width
                + ImGui.getStyle().windowPadding.x
                + ImGui.getStyle().itemSpacing.x
    );
}

fun endColumnGroup() {
    ImGui.columns(1, "##TreeColumns", false)
}