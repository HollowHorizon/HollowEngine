package ru.hollowhorizon.hollowengine.common.scripting.core.completion

import imgui.ImGui
import imgui.ImVec2
import imgui.ImVec4
import imgui.flag.ImGuiCol
import ru.hollowhorizon.hc.client.imgui.Graphics
import ru.hollowhorizon.hc.client.utils.mc
import ru.hollowhorizon.hc.client.utils.rl
import ru.hollowhorizon.hc.client.utils.toTexture
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.TextFileData
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.completionsList
import ru.hollowhorizon.hollowengine.client.gui.times
import kotlin.math.max

data class CompletionVariant(
    val text: String,
    val displayText: String,
    val tail: String,
    val icon: Icon,
) {
    override fun toString() = displayText

    fun render(file: TextFileData) {
        ImGui.pushStyleColor(ImGuiCol.HeaderHovered, 0xFF4A4543.toInt())
        val textColor = when (icon) {
            Icon.METHOD -> ImVec4(0.878f, 0.568f, 0.098f, 1f)
            Icon.CLASS -> ImVec4(0.098f, 0.521f, 0f, 1f)
            Icon.VARIABLE -> ImVec4(0f, 0.456f, 0.721f, 1f)
            Icon.PACKAGE -> ImVec4(0.537f, 0.329f, 0.921f, 1f)
            else -> ImVec4(1f, 1f, 1f, 1f)
        }
        ImGui.pushStyleColor(ImGuiCol.Text, 0xFFCCCCCC.toInt())

        Graphics.apply {
            val pos = ImGui.getCursorPos()
            val displayText = displayText + if (icon == Icon.CLASS) tail else ""

            val clicked = ImGui.selectable("##$displayText")
            ImGui.setCursorPos(pos)
            val fontSize = ImGui.getFontSize().toFloat()
            ImGui.image(
                "hollowengine:textures/gui/icons/autocomplete_${icon.name.lowercase()}.png".rl.toTexture().id.toLong(),
                ImVec2(fontSize, fontSize), ImVec2(0f, 0f), ImVec2(1f, 1f), textColor.times(1.2f)
            )
            ImGui.sameLine()
            val tailSize = ImGui.calcTextSizeX(if (tail == "Unit" || icon == Icon.CLASS) "" else tail)
            val size = (ImGui.getContentRegionMaxX() - fontSize - tailSize)
            var display = displayText
            var isChanged = false
            while (ImGui.calcTextSizeX(display) > size && display.isNotEmpty()) {
                display = display.substring(0, display.length - 1)
                isChanged = true
            }
            if (isChanged && display.length > 4) {
                display = display.substring(0, display.length - 4)
                display += "..."
            }
            text(display, shadow = false)
            if(isChanged) tooltipHover {
                ImGui.pushTextWrapPos(mc.window.width * 0.9f)
                ImGui.textWrapped(displayText)
                ImGui.popTextWrapPos()
            }
            if (tail != "Unit" && icon != Icon.PACKAGE && icon != Icon.CLASS) withColors(
                ImGuiCol.Text to 0x88888888.toInt()
            ) {
                ImGui.sameLine()
                ImGui.setCursorPosX(ImGui.getContentRegionMaxX() - tailSize - 5)
                textShadow(tail)
            }

            if (clicked) {
                val editor = file.textEditor
                val original = editor.currentLineText

                val column = editor.cursorPosition.mColumn

                val beforeIndex = original.substring(0, column)
                var charPos = beforeIndex.lastIndexOf('.') + 1

                sequenceOf('(', '[', '{', ' ').forEach {
                    charPos = max(charPos, beforeIndex.lastIndexOf(it) + 1)
                }
                charPos = charPos.coerceAtLeast(0)
                val lines = editor.textLines
                val end = if (text.endsWith("(")) ")" else ""
                lines[editor.cursorPosition.mLine] =
                    original.substring(0, charPos) + text + end + original.substring(column)
                editor.textLines = lines
                editor.setCursorPosition(editor.cursorPosition.mLine, charPos + text.length)
                completionsList.clear()
                file.code = file.textEditor.text.substringBeforeLast("\n")
                file.save()
            }
        }

        ImGui.popStyleColor(2)
    }

    val textWidth: Float
        get() {
            val spacing = ImGui.getStyle().itemSpacingX

            val displayWidth = ImGui.calcTextSizeX(displayText)
            val tailWidth = if (tail == "Unit") 0f else ImGui.calcTextSizeX(tail)
            val iconWidth = ImGui.getFontSize().toFloat()

            return iconWidth + spacing + displayWidth + spacing + tailWidth
        }

    enum class Icon {
        PACKAGE, CLASS, METHOD, VARIABLE, UNKNOWN
    }
}