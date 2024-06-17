package ru.hollowhorizon.hollowengine.client.gui.npcs

import imgui.ImGui
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiMouseButton
import imgui.flag.ImGuiStyleVar

object BehaviorLayers {
    var selected = 0

    fun draw(list: MutableList<String>) {
        val windowSize = ImGui.getWindowSize()
        ImGui.setCursorPos(windowSize.x * 0.75f - 15f, windowSize.y * 0.75f - 15f)

        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 8f, 8f)
        ImGui.pushStyleVar(ImGuiStyleVar.ChildRounding, 15f)
        ImGui.pushStyleVar(ImGuiStyleVar.ChildBorderSize, 3f)
        ImGui.beginChild("Слои", windowSize.x / 4, windowSize.y / 4, true)
        val size = ImGui.getWindowSize()
        ArrayList(list).forEachIndexed { index, s ->
            drawEntry(list, index, s, size.x, 50f, index == selected)
        }
        ImGui.endChild()
        ImGui.popStyleVar(3)
    }

    private fun drawEntry(
        list: MutableList<String>,
        index: Int,
        entry: String,
        width: Float,
        height: Float,
        selected: Boolean,
    ) {
        if (selected) {
            val col = ImGui.getStyle().getColor(ImGuiCol.ButtonHovered)
            ImGui.pushStyleColor(ImGuiCol.Button, col.x, col.y, col.z, col.w)
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, col.x, col.y, col.z, col.w)
        }
        if(ImGui.button(entry, width, height)) this.selected = index
        ImGui.pushStyleVar(ImGuiStyleVar.PopupBorderSize, 0f)
        ImGui.pushStyleColor(ImGuiCol.PopupBg, 0f, 0f, 0f, 0f)
        //ImGui.pushStyleVar(ImGuiStyleVar.WindowBorderSize, 0f)
        //ImGui.pushStyleVar(ImGuiStyleVar.FrameBorderSize, 0f)
        //ImGui.pushStyleVar(ImGuiStyleVar.ChildBorderSize, 0f)
        if (ImGui.beginDragDropTarget()) {
            val old = ImGui.getDragDropPayload<Int>("index")
            val current = list[index]
            list[index] = list[old]
            list[old] = current
            if(selected) this.selected = old
            else if(old == this.selected) this.selected = index
            ImGui.endDragDropTarget()
        }
        if (ImGui.beginDragDropSource()) {
            ImGui.setDragDropPayload("index", index, ImGuiMouseButton.Left)
            ImGui.endDragDropSource()
        }
        ImGui.popStyleVar()
        ImGui.popStyleColor()
        if (selected) ImGui.popStyleColor(2)
    }
}