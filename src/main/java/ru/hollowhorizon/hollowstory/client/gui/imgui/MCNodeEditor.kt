package ru.hollowhorizon.hollowstory.client.gui.imgui

import com.mojang.blaze3d.matrix.MatrixStack
import imgui.ImGui
import imgui.extension.imnodes.ImNodesContext
import imgui.extension.nodeditor.NodeEditor
import imgui.extension.nodeditor.NodeEditorContext
import imgui.extension.nodeditor.flag.NodeEditorPinKind
import imgui.flag.ImGuiDir
import imgui.flag.ImGuiStyleVar
import imgui.flag.ImGuiWindowFlags
import imgui.internal.flag.ImGuiDockNodeFlags
import imgui.type.ImInt
import imgui.type.ImLong
import net.minecraft.client.gui.screen.Screen
import ru.hollowhorizon.hc.client.utils.toSTC
import ru.hollowhorizon.hollowstory.client.gui.IMGUIHandler

class MCNodeEditor : Screen("Node Editor".toSTC()) {
    val DOCKSPACE_ID = "main_dockspace"

    private val nodeContext = ImNodesContext()
    private val URL = "https://github.com/thedmd/imgui-node-editor/tree/687a72f940"
    private val context = NodeEditorContext()
    private val graph = Graph()

    override fun init() {
        super.init()
        minecraft?.mouseHandler?.releaseMouse()
    }

    override fun render(p_230430_1_: MatrixStack, p_230430_2_: Int, p_230430_3_: Int, p_230430_4_: Float) =
        IMGUIHandler.render(0, 0, width, height) {
            val flags = ImGuiWindowFlags.NoNavFocus.orEquals(
                ImGuiWindowFlags.NoTitleBar,
                ImGuiWindowFlags.NoCollapse,
                ImGuiWindowFlags.NoResize,
                ImGuiWindowFlags.NoMove,
                ImGuiWindowFlags.NoBringToFrontOnFocus
            )
            val size = ImGui.getIO().displaySize
            ImGui.setNextWindowPos(0f, 0f)
            ImGui.setNextWindowSize(size.x, size.y)
            ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 0f, 0f)
            ImGui.begin("Window##$narrationMessage", flags)

            ImGui.setNextWindowViewport(ImGui.getMainViewport().id)
            ImGui.popStyleVar()

//            if (ImGui.button("Масштаб")) {
//                NodeEditor.navigateToContent(1F)
//            }

            NodeEditor.setCurrentEditor(context)
            NodeEditor.begin("Редактор Узлов")

            for (node in graph.nodes.values) {
                NodeEditor.beginNode(node.nodeId.toLong())
                ImGui.spacing()

                ImGui.text(node.name)

                NodeEditor.beginPin(node.inputPinId.toLong(), NodeEditorPinKind.Input)
                ImGui.text("Вход")
                NodeEditor.endPin()

                ImGui.sameLine()

                NodeEditor.beginPin(node.outputPinId.toLong(), NodeEditorPinKind.Output)
                ImGui.text("Выход")
                NodeEditor.endPin()

                NodeEditor.endNode()
            }

            if (NodeEditor.beginCreate()) {
                val a = ImLong()
                val b = ImLong()
                if (NodeEditor.queryNewLink(a, b)) {
                    val source = graph.findByOutput(a.get())
                    val target = graph.findByInput(b.get())
                    if (source != null && target != null && !source.outputNodes.contains(target.nodeId) && NodeEditor.acceptNewItem()) {
                        source.outputNodes.add(target.nodeId)
                    }
                }
            }
            NodeEditor.endCreate()

            var uniqueLinkId = 1
            for (node in graph.nodes.values) {
                for (outputNodeId in node.outputNodes) {
                    val outputNode = graph.nodes[outputNodeId] ?: continue
                    NodeEditor.link(uniqueLinkId++.toLong(), node.outputPinId.toLong(), outputNode.inputPinId.toLong())
                    uniqueLinkId++
                }
            }

            NodeEditor.suspend()

            val nodeWithContextMenu = NodeEditor.getNodeWithContextMenu()
            if (nodeWithContextMenu >= 0) {
                ImGui.openPopup("node_context")
                ImGui.getStateStorage().setInt(ImGui.getID("delete_node_id"), nodeWithContextMenu.toInt());
            }

            if (ImGui.isPopupOpen("node_context")) {
                val targetNode = ImGui.getStateStorage().getInt(ImGui.getID("delete_node_id"));
                if (ImGui.beginPopup("node_context")) {
                    if (ImGui.button("Удалить " + graph.nodes[targetNode]?.name)) {
                        graph.nodes.remove(targetNode)
                        ImGui.closeCurrentPopup()
                    }
                    ImGui.endPopup()
                }
            }

            if (NodeEditor.showBackgroundContextMenu()) {
                ImGui.openPopup("node_editor_context")
            }

            if (ImGui.beginPopup("node_editor_context")) {
                if (ImGui.button("Создать Узел")) {
                    val node = graph.createGraphNode()
                    val canvasX = NodeEditor.toCanvasX(ImGui.getMousePosX())
                    val canvasY = NodeEditor.toCanvasY(ImGui.getMousePosY())
                    NodeEditor.setNodePosition(node.nodeId.toLong(), canvasX, canvasY)
                    ImGui.closeCurrentPopup()
                }
                ImGui.endPopup()
            }

            NodeEditor.resume()
            NodeEditor.end()

            ImGui.end()

        }

    private fun createDock(name: String) {
        val viewport = ImGui.getWindowViewport()
        val dockspaceID = ImGui.getID(DOCKSPACE_ID)
        imgui.internal.ImGui.dockBuilderRemoveNode(dockspaceID)
        imgui.internal.ImGui.dockBuilderAddNode(dockspaceID, ImGuiDockNodeFlags.DockSpace)
        imgui.internal.ImGui.dockBuilderSetNodeSize(dockspaceID, viewport.sizeX, viewport.sizeY)
        val dockMainId = ImInt(dockspaceID)
        val dockLeft: Int =
            imgui.internal.ImGui.dockBuilderSplitNode(dockMainId.get(), ImGuiDir.Right, 0.25f, null, dockMainId)
        imgui.internal.ImGui.dockBuilderDockWindow("Editor##$name", dockMainId.get())
        imgui.internal.ImGui.dockBuilderDockWindow("Nodes##$name", dockLeft)
        imgui.internal.ImGui.dockBuilderFinish(dockspaceID)
    }
}


class Graph {
    var nextNodeId = 1
    var nextPinId = 100
    val nodes: MutableMap<Int, GraphNode> = HashMap()

    init {
        val first = createGraphNode()
        val second = createGraphNode()
        first.outputNodes.add(second.nodeId)
    }

    fun createGraphNode(): GraphNode {
        val node = GraphNode(nextNodeId++, nextPinId++, nextPinId++)
        nodes[node.nodeId] = node
        return node
    }

    fun findByInput(inputPinId: Long): GraphNode? {
        for (node in nodes.values) {
            if (node.inputPinId.toLong() == inputPinId) {
                return node
            }
        }
        return null
    }

    fun findByOutput(outputPinId: Long): GraphNode? {
        for (node in nodes.values) {
            if (node.outputPinId.toLong() == outputPinId) {
                return node
            }
        }
        return null
    }

    class GraphNode(val nodeId: Int, val inputPinId: Int, val outputPinId: Int) {
        var outputNodes = arrayListOf<Int>()

        val name: String
            get() = "Узел " + (64 + nodeId).toChar()
    }
}

fun Int.orEquals(vararg ints: Int): Int {
    var out = this
    for (element in ints) out = out or element
    return out
}