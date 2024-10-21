package ru.hollowhorizon.hollowengine.client.gui.scripting.files

import com.mojang.blaze3d.platform.InputConstants
import imgui.ImGui
import imgui.extension.texteditor.TextEditor
import imgui.flag.ImGuiCol
import imgui.type.ImBoolean
import net.minecraft.client.Minecraft
import org.lwjgl.glfw.GLFW
import ru.hollowhorizon.hollowengine.client.gui.scripting.IDEGui
import ru.hollowhorizon.hollowengine.client.gui.scripting.SaveFilePacket

var changeTime = 0.0

class TextFileData(name: String, path: String, open: ImBoolean, var code: String) : FileData(name, path, open) {
    override fun draw() {
        ImGui.pushStyleColor(ImGuiCol.TextSelectedBg, 1f, 1f, 1f, 1f)

        if (path.startsWith("%")) IDEGui.editor.isReadOnly = true
        if (IDEGui.editor.text.substringBeforeLast('\n') != code) IDEGui.editor.text = code

        IDEGui.currentFile = name
        IDEGui.currentPath = path
        val startPos = ImGui.getCursorScreenPos()

        IDEGui.editor.render("Code Editor")

        if (IDEGui.editor.isTextChanged) IDEGui.editor.text =
            IDEGui.editor.text.substringBeforeLast("\n").replace("\t", "    ")


        val line = IDEGui.editor.currentLineText

        if (openPopup && !(GLFW.GLFW_KEY_SPACE..GLFW.GLFW_KEY_Z).any {
                InputConstants.isKeyDown(
                    Minecraft.getInstance().window.window,
                    it
                )
            }) {
            ImGui.openPopup("##completions")
            openPopup = false
        }

        if (ImGui.beginDragDropTarget()) {
            val payload = ImGui.acceptDragDropPayload<Any?>("TREE")
            if (payload != null) {
                val data = payload.toString().substringAfter('/').replaceFirst('/', ':')
                IDEGui.insertAtCursor("\"$data\"")
            }
            ImGui.endDragDropTarget()
        }

        if (IDEGui.editor.isTextChanged) {
            code = IDEGui.editor.text.substringBeforeLast("\n")
            save()
        }

        if (IDEGui.shouldClose) ImGui.setKeyboardFocusHere(-1)

        IDEGui.drawScriptPopup()
        if (ImGui.isItemHovered() && ImGui.isMouseClicked(1)) ImGui.openPopup("ScriptPopup")

        if (path.startsWith("%")) IDEGui.editor.isReadOnly = false

        ImGui.popStyleColor()
    }

    override fun save() {
        if (path.startsWith("%")) return
        SaveFilePacket(path, code.toByteArray()).send()
    }
}

var openPopup = false

val TextEditor.index: Int
    get() {
        val line = cursorPosition.mLine
        val column = cursorPosition.mColumn
        var newIndex = 0
        var lineIndex = 0
        for (textLine in textLines) {
            if (lineIndex == line) break
            newIndex += textLine.length + 1
            lineIndex++
        }
        newIndex += column
        return newIndex - 1
    }