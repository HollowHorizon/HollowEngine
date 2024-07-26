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
import imgui.extension.nodeditor.NodeEditor
import imgui.extension.nodeditor.NodeEditorConfig
import imgui.extension.nodeditor.NodeEditorContext
import imgui.extension.nodeditor.flag.NodeEditorPinKind
import imgui.type.ImLong
import net.minecraft.locale.Language
import ru.hollowhorizon.hc.client.imgui.FontAwesomeIcons
import ru.hollowhorizon.hollowengine.HollowEngine.Companion.MODID
import ru.hollowhorizon.hollowengine.client.translate
import ru.hollowhorizon.hollowengine.common.npcs.CURRENT_GRAPH
import ru.hollowhorizon.hollowengine.common.npcs.ScriptGraph
import ru.hollowhorizon.hollowengine.common.npcs.connections.Connection
import ru.hollowhorizon.hollowengine.common.npcs.nodes.ScriptNode
import ru.hollowhorizon.hollowengine.common.npcs.nodes.pins.Pin
import ru.hollowhorizon.hollowengine.common.npcs.nodes.pins.isConnected
import ru.hollowhorizon.hollowengine.common.registry.NodesRegistry
import kotlin.math.max

object NodeEditorV2 {
    val config = NodeEditorConfig().apply {
        settingsFile = "hollowengine/nodes.json"
    }
    val context = NodeEditorContext(config)

    fun draw(graph: ScriptGraph) {
        NodeEditor.setCurrentEditor(context)
        NodeEditor.begin("Node Editor")

        val languageManager = Language.getInstance()
        CURRENT_GRAPH = graph
        var nodeId = 1
        var pinId = 33000
        val nodeMap = HashMap<ScriptNode, Int>()
        val pinMap = HashMap<Pin<*>, Int>()

        for (node in graph.nodes) {
            var id = nodeId++
            ImGui.pushID(id)
            nodeMap[node] = id
            NodeEditor.beginNode(id.toLong())
            ImGui.text(languageManager.getOrDefault("nodes.${node.type.namespace}.${node.type.path.replace('/', '.')}"))
            ImGui.sameLine()
            ImGui.text(FontAwesomeIcons.InfoCircle)
            var tooltip = {}
            if (ImGui.isItemHovered()) tooltip = {
                ImGui.beginTooltip()
                val pattern = "nodes.${node.type.namespace}.${node.type.path.replace('/', '.')}.desc"
                val desc = if (languageManager.has(pattern)) languageManager.getOrDefault(pattern) else "nodes.$MODID.no_desc". translate + "."
                ImGui.textColored(ImGui.colorConvertFloat4ToU32(1f, 0.84313726f, 0f, 1f), desc)
                ImGui.endTooltip()
            }

            val pos = ImGui.getCursorPos()
            var maxX = pos.x
            var maxY = pos.y

            node.inputs.forEach { pin ->
                id = pinId++
                pinMap[pin] = id
                NodeEditor.beginPin(id.toLong(), NodeEditorPinKind.Input)
                ImGui.pushID(id)
                ImGui.text(languageManager.getOrDefault(pin.name))
                ImGui.sameLine()
                maxX = max(maxX, ImGui.getCursorPosX())
                NodeEditor.endPin()
                if (!pin.isConnected) pin.pick()
                ImGui.sameLine()
                maxX = max(maxX, ImGui.getCursorPosX())
                ImGui.newLine()
                ImGui.popID()
                maxY = max(maxY, ImGui.getCursorPosY())
            }

            ImGui.setCursorPos(maxX + 20, pos.y)

            node.outputs.forEach { pin ->
                id = pinId++
                pinMap[pin] = id
                NodeEditor.beginPin(id.toLong(), NodeEditorPinKind.Output)
                ImGui.pushID(id)
                ImGui.text(languageManager.getOrDefault(pin.name))
                ImGui.sameLine()
                ImGui.popID()
                NodeEditor.endPin()

                ImGui.setCursorPosX(maxX + 20)
                maxY = max(maxY, ImGui.getCursorPosY())
            }

            ImGui.setCursorPos(pos.x, maxY)

            node.draw(graph)

            NodeEditor.endNode()

            NodeEditor.suspend()
            node.drawPost(graph)
            tooltip()
            NodeEditor.resume()

            ImGui.popID()
        }

        if (NodeEditor.beginCreate()) {
            val a = ImLong()
            val b = ImLong()
            if (NodeEditor.queryNewLink(a, b)) {
                fun connect() {
                    val first = a.get().toInt()
                    val second = b.get().toInt()

                    val inputId = pinMap.entries.find { it.value == first }?.key ?: return
                    val outputId = pinMap.entries.find { it.value == second }?.key ?: return

                    val inputNode =
                        graph.nodes.find { it.inputs.contains(inputId) || it.outputs.contains(inputId) } ?: return
                    val outputNode =
                        graph.nodes.find { it.inputs.contains(outputId) || it.outputs.contains(outputId) } ?: return

                    if (inputId.type != outputId.type) return
                    if (inputNode == outputNode) return

                    if(NodeEditor.acceptNewItem()) graph.connections += Connection(inputNode, outputNode, inputId, outputId)
                }
                connect()
            }
        }
        NodeEditor.endCreate()

        var uniqueLinkId = 1
        for (link in graph.connections) {
            NodeEditor.link(
                uniqueLinkId++.toLong(),
                pinMap[link.inputPin]!!.toLong(),
                pinMap[link.outputPin]!!.toLong()
            )
        }

        NodeEditor.suspend()

        val nodeWithContextMenu = NodeEditor.getNodeWithContextMenu()
        val linkWithContextMenu = NodeEditor.getLinkWithContextMenu()
        if (nodeWithContextMenu != -1L) {
            ImGui.openPopup("node_context")
            ImGui.getStateStorage().setInt(ImGui.getID("delete_node_id"), nodeWithContextMenu.toInt())
        } else if (linkWithContextMenu != -1L) {
            ImGui.openPopup("link_context")
            ImGui.getStateStorage().setInt(ImGui.getID("delete_link_id"), linkWithContextMenu.toInt())
        } else if (NodeEditor.showBackgroundContextMenu()) {
            ImGui.openPopup("node_editor_context")
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
//                    val config = EditorIniFile.read(ImNodes.saveEditorStateToIniString(ru.hollowhorizon.hollowengine.client.gui.NodeEditor.nodeContext))
//                    for (i in targetNode + 1..nodeId - 1) {
//                        config.properties["node.${i - 1}"] = config.properties["node.$i"] ?: continue
//                    }
                    graph.nodes.remove(node)
                    graph.connections.removeIf { it.output == node || it.input == node }
                    val cfg = config.toString()
                    //ImNodes.loadEditorStateFromIniString(ru.hollowhorizon.hollowengine.client.gui.NodeEditor.nodeContext, cfg, cfg.length)
                    ImGui.closeCurrentPopup()
                }
                ImGui.endPopup()
            }
        }

        if (ImGui.isPopupOpen("link_context")) {
            val targetLink = ImGui.getStateStorage().getInt(ImGui.getID("delete_link_id"))
            val node = nodeMap.entries.find { it.value == targetLink }?.key
            if (node != null && ImGui.beginPopup("link_context")) {
                if (ImGui.button("nodes.$MODID.unlink")) {
                    graph.connections.removeAt(targetLink - 1)
                    ImGui.closeCurrentPopup()
                }
                ImGui.endPopup()
            }
        }

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

        if (!ru.hollowhorizon.hollowengine.client.gui.NodeEditor.isLoaded) {
            ru.hollowhorizon.hollowengine.client.gui.NodeEditor.isLoaded = true
            //ImNodes.loadEditorStateFromIniString(ru.hollowhorizon.hollowengine.client.gui.NodeEditor.nodeContext, graph.editorInfo, graph.editorInfo.length)
        }

        //tooltip()

        NodeEditor.resume()
        NodeEditor.end()

        //graph.editorInfo = ImNodes.saveEditorStateToIniString(ru.hollowhorizon.hollowengine.client.gui.NodeEditor.nodeContext)
    }
}