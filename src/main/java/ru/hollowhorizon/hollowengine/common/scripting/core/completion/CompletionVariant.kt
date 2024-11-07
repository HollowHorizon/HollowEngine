package ru.hollowhorizon.hollowengine.common.scripting.core.completion

import imgui.ImGui
import imgui.ImVec4
import imgui.extension.texteditor.TextEditor
import imgui.flag.ImGuiCol
import ru.hollowhorizon.hc.client.imgui.Graphics
import ru.hollowhorizon.hc.client.utils.rl

data class CompletionVariant(
    val text: String,
    val displayText: String,
    val tail: String,
    val icon: Icon,
) {
    override fun toString(): String {
        return displayText
    }

    fun render(editor: TextEditor) {
        ImGui.pushStyleColor(ImGuiCol.HeaderHovered, ImVec4(0.1f, 1f, 1f, 0.4f))

        Graphics.apply {
            val pos = ImGui.getCursorPos()
            val clicked = ImGui.selectable("##$displayText")
            ImGui.setCursorPos(pos)
            val fontSize = ImGui.getFontSize().toFloat()
            image("hollowengine:textures/gui/icons/autocomplete_${icon.name.lowercase()}.png".rl, fontSize, fontSize)
            ImGui.sameLine()
            textShadow(displayText)
            ImGui.sameLine()
            if(tail != "Unit") withColors(ImGuiCol.Text to ImVec4(0.6f, 0.6f, 0.6f, 1f).color) {
                val size = ImGui.calcTextSize(tail)
                ImGui.setCursorPosX(ImGui.getContentRegionMaxX() - size.x)
                textShadow(tail)
            }

            if(clicked) {

            }
        }

        ImGui.popStyleColor()
    }

    enum class Icon {
        CLASS, METHOD, VARIABLE, PACKAGE, UNKNOWN
    }
}