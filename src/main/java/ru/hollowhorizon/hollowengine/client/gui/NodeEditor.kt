/*
 * MIT License
 *
 * Copyright (c) 2024 HollowHorizon
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package ru.hollowhorizon.hollowengine.client.gui

import imgui.ImGui
import imgui.extension.imnodes.ImNodes
import imgui.extension.imnodes.ImNodesContext
import imgui.extension.imnodes.flag.ImNodesColorStyle
import imgui.extension.imnodes.flag.ImNodesMiniMapLocation
import imgui.extension.imnodes.flag.ImNodesPinShape
import imgui.flag.ImGuiKey
import imgui.flag.ImGuiMouseButton
import imgui.type.ImInt
import net.minecraft.locale.Language
import net.minecraft.resources.ResourceLocation
import ru.hollowhorizon.hc.client.imgui.FontAwesomeIcons
import ru.hollowhorizon.hollowengine.common.npcs.CURRENT_GRAPH
import ru.hollowhorizon.hollowengine.common.npcs.ScriptGraph
import ru.hollowhorizon.hollowengine.common.npcs.connections.Connection
import ru.hollowhorizon.hollowengine.common.npcs.nodes.ScriptNode
import ru.hollowhorizon.hollowengine.common.npcs.nodes.pins.Pin
import ru.hollowhorizon.hollowengine.common.npcs.nodes.pins.isConnected
import ru.hollowhorizon.hollowengine.common.registry.NodesRegistry
import ru.hollowhorizon.hollowengine.common.util.EditorIniFile
import kotlin.math.max

object NodeEditor {
    val nodeContext = ImNodesContext()

    private val LINK_A = ImInt()
    private val LINK_B = ImInt()
    var isLoaded = false

    fun render(graph: ScriptGraph) {
        val languageManager = Language.getInstance()
        CURRENT_GRAPH = graph
        var nodeId = 1
        var pinId = 33000
        val nodeMap = HashMap<ScriptNode, Int>()
        val pinMap = HashMap<Pin<*>, Int>()

        ImNodes.editorContextSet(nodeContext)
        ImNodes.beginNodeEditor()
        ImNodes.pushColorStyle(
            ImNodesColorStyle.TitleBar,
            ImGui.colorConvertFloat4ToU32(0.921f, 0.631f, 0.2f, 0.9f)
        )
        ImNodes.pushColorStyle(
            ImNodesColorStyle.TitleBarHovered,
            ImGui.colorConvertFloat4ToU32(1f, 0.681f, 0.25f, 1f)
        )
        ImNodes.pushColorStyle(
            ImNodesColorStyle.TitleBarSelected,
            ImGui.colorConvertFloat4ToU32(1f, 0.681f, 0.25f, 1f)
        )
        ImNodes.pushColorStyle(
            ImNodesColorStyle.Link,
            ImGui.colorConvertFloat4ToU32(0.921f, 0.631f, 0.2f, 0.9f)
        )
        ImNodes.pushColorStyle(
            ImNodesColorStyle.Pin,
            ImGui.colorConvertFloat4ToU32(0.921f, 0.631f, 0.2f, 0.9f)
        )
        ImNodes.pushColorStyle(
            ImNodesColorStyle.GridBackground,
            ImGui.colorConvertFloat4ToU32(0.3f, 0.181f, 0f, 0.75f)
        )
        ImNodes.pushColorStyle(
            ImNodesColorStyle.GridLine,
            ImGui.colorConvertFloat4ToU32(1f, 1f, 1f, 0.4f)
        )

        for (node in graph.nodes) {
            var id = nodeId++
            nodeMap[node] = id
            ImNodes.beginNode(id)
            ImNodes.beginNodeTitleBar()
            val posY = ImGui.getCursorPosY()
            ImGui.text(languageManager.getOrDefault("nodes.${node.type.namespace}.${node.type.path.replace('/', '.')}"))
            ImGui.sameLine()
            val posX = ImGui.getCursorPosX()
            ImNodes.endNodeTitleBar()

            val pos = ImGui.getCursorPos()
            var maxX = pos.x
            var maxY = pos.y

            node.inputs.forEach { pin ->
                id = pinId++
                pinMap[pin] = id
                ImNodes.beginInputAttribute(id, ImNodesPinShape.Quad)
                ImGui.text(languageManager.getOrDefault(pin.name))
                if (!pin.isConnected) pin.pick()
                ImGui.sameLine()
                maxX = max(maxX, ImGui.getCursorPosX())
                maxY = max(maxY, ImGui.getCursorPosY())
                ImNodes.endInputAttribute()
            }

            ImGui.setCursorPos(maxX + 20, pos.y)

            node.outputs.forEach { pin ->
                id = pinId++
                pinMap[pin] = id
                ImNodes.beginOutputAttribute(id, ImNodesPinShape.QuadFilled)
                ImGui.text(languageManager.getOrDefault(pin.name))
                ImGui.sameLine()
                maxY = max(maxY, ImGui.getCursorPosY())
                ImNodes.endOutputAttribute()

                ImGui.setCursorPosX(maxX + 20)
            }

            ImGui.setCursorPos(pos.x, maxY)
            ImGui.newLine()
            node.draw(graph)

            ImGui.setCursorPos(posX, posY)
            ImGui.text(FontAwesomeIcons.InfoCircle)
            if (ImGui.isItemHovered()) {
                ImGui.beginTooltip()
                val pattern = "nodes.${node.type.namespace}.${node.type.path.replace('/', '.')}.desc"
                val desc = if (languageManager.has(pattern)) languageManager.getOrDefault(pattern) else "Описания нет."
                ImGui.textColored(ImGui.colorConvertFloat4ToU32(1f, 0.84313726f, 0f, 1f), desc)
                ImGui.endTooltip()
            }
            ImGui.setCursorPos(pos.x, pos.y)

            ImNodes.endNode()
        }

        var uniqueLinkId = 1
        for (link in graph.connections) {
            ImNodes.link(
                uniqueLinkId++,
                pinMap[link.inputPin]!!,
                pinMap[link.outputPin]!!
            )
        }

        val isEditorHovered = ImNodes.isEditorHovered()

        ImNodes.miniMap(0.2f, ImNodesMiniMapLocation.BottomRight)
        ImNodes.endNodeEditor()

        if (ImNodes.isLinkCreated(LINK_A, LINK_B)) {
            val first = LINK_A.get()
            val second = LINK_B.get()

            val inputId = pinMap.entries.find { it.value == first }?.key ?: return
            val outputId = pinMap.entries.find { it.value == second }?.key ?: return

            val inputNode = graph.nodes.find { it.inputs.contains(inputId) || it.outputs.contains(inputId) } ?: return
            val outputNode =
                graph.nodes.find { it.inputs.contains(outputId) || it.outputs.contains(outputId) } ?: return

            if (inputId.type != outputId.type) return
            if (inputNode == outputNode) return

            graph.connections += Connection(inputNode, outputNode, inputId, outputId)
        }

        if (ImGui.getIO().getKeysDown(ImGui.getKeyIndex(ImGuiKey.Delete))) {
            val selectedLinks = IntArray(ImNodes.numSelectedLinks())
            ImNodes.getSelectedLinks(selectedLinks)

            //graph.deleteLinks(selectedLinks)

            val selectedNodes = IntArray(ImNodes.numSelectedNodes())
            ImNodes.getSelectedNodes(selectedNodes)

            //selectedNodes.forEach(graph::deleteNode)
        }

        if (ImGui.isMouseClicked(ImGuiMouseButton.Right)) {
            val hoveredNode = ImNodes.getHoveredNode()
            val hoveredLink = ImNodes.getHoveredLink()
            if (hoveredNode != -1) {
                ImGui.openPopup("node_context")
                ImGui.getStateStorage().setInt(ImGui.getID("delete_node_id"), hoveredNode)
            } else if (hoveredLink != -1) {
                ImGui.openPopup("link_context")
                ImGui.getStateStorage().setInt(ImGui.getID("delete_link_id"), hoveredLink)
            } else if (isEditorHovered) {
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
                    val config = EditorIniFile.read(ImNodes.saveEditorStateToIniString(nodeContext))
                    for (i in targetNode + 1..nodeId - 1) {
                        config.properties["node.${i - 1}"] = config.properties["node.$i"] ?: continue
                    }
                    graph.nodes.remove(node)
                    graph.connections.removeIf { it.output == node || it.input == node }
                    val cfg = config.toString()
                    ImNodes.loadEditorStateFromIniString(nodeContext, cfg, cfg.length)
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

        if (ImGui.beginPopup("node_editor_context")) {
            NodesRegistry.entries().keys.toTree().drawMenu()?.let {
                graph.nodes += NodesRegistry.find<ScriptNode>(it)
                ImNodes.setNodeScreenSpacePos(
                    nodeId,
                    ImGui.getMousePosX(),
                    ImGui.getMousePosY()
                )
                ImGui.closeCurrentPopup()
            }

            ImGui.endPopup()
        }

        if(!isLoaded) {
            isLoaded = true
            ImNodes.loadEditorStateFromIniString(nodeContext, graph.editorInfo, graph.editorInfo.length)
        }

        graph.editorInfo = ImNodes.saveEditorStateToIniString(nodeContext)
    }
}

open class Tree(val name: String) {
    val childen = HashMap<String, Tree>()

    fun insert(value: String, result: ResourceLocation) {
        val values = value.split('/', limit = 2)
        if (values.size < 2) childen[values[0]] = Leaf(result)
        else childen.computeIfAbsent(values[0]) { Tree(values[0]) }.insert(values[1], result)
    }

    fun drawMenu(): ResourceLocation? {
        var result: ResourceLocation? = null
        for ((name, child) in childen.toSortedMap()) {
            if (child is Leaf) {
                if (ImGui.menuItem(
                        Language.getInstance()
                            .getOrDefault("nodes.${child.value.namespace}.${child.value.path.replace('/', '.')}")
                    )
                ) {
                    result = child.value
                }
            } else {
                if (ImGui.beginMenu(Language.getInstance().getOrDefault("node_categories.$name"))) {
                    val res = child.drawMenu()
                    if (result == null) result = res
                    ImGui.endMenu()
                }
            }
        }
        return result
    }

    class Leaf(val value: ResourceLocation) : Tree("Leaf")
}

fun Collection<ResourceLocation>.toTree(): Tree {
    val root = Tree("ROOT")

    for (item in this.sorted()) {
        root.insert(item.path, item)
    }
    return root
}