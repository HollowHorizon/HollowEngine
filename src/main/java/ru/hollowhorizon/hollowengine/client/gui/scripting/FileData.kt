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

package ru.hollowhorizon.hollowengine.client.gui.scripting

import imgui.ImGui
import imgui.extension.texteditor.TextEditor
import imgui.extension.texteditor.flag.TextEditorPaletteIndex
import imgui.type.ImBoolean
import net.minecraft.client.renderer.texture.DynamicTexture
import ru.hollowhorizon.hollowengine.client.gui.NodeEditor
import ru.hollowhorizon.hollowengine.common.npcs.ScriptGraph
import kotlin.math.min

abstract class FileData(
    val name: String,
    val path: String,
    val open: ImBoolean,
) {
    abstract fun draw()

    abstract fun save()
}

class ScriptFileData(name: String, path: String, open: ImBoolean, var code: String) : FileData(name, path, open) {
    override fun draw() {
        if (IDEGui.editor.text.substringBeforeLast('\n') != code) IDEGui.editor.text = code
        IDEGui.currentFile = name
        IDEGui.currentPath = path
        IDEGui.editor.render("Code Editor")

        if (ImGui.beginDragDropTarget()) {
            val payload = ImGui.acceptDragDropPayload<Any?>("TREE")
            if (payload != null) {
                val data = payload.toString().substringAfter('/').replaceFirst('/', ':')
                IDEGui.insertAtCursor("\"$data\"")
            }
            ImGui.endDragDropTarget()
        }

        if (IDEGui.shouldClose) {
            ImGui.setKeyboardFocusHere(-1)
        }
        if (IDEGui.editor.isTextChanged) {
            val text = IDEGui.editor.currentLineText
            if (IDEGui.editor.cursorPositionColumn - 1 in text.indices) IDEGui.complete(text[IDEGui.editor.cursorPositionColumn - 1])
            code = IDEGui.editor.text.substringBeforeLast("\n")
            save()
        }

        IDEGui.drawScriptPopup()
        if (ImGui.isItemHovered() && ImGui.isMouseClicked(1)) {
            ImGui.openPopup("ScriptPopup")
        }
    }

    override fun save() {
        SaveFilePacket(path, code.toByteArray()).send()
    }
}

class NodesFileData(name: String, path: String, open: ImBoolean) : FileData(name, path, open) {
    val scriptGraph = ScriptGraph()

    override fun draw() {
        IDEGui.currentFile = name
        IDEGui.currentPath = path

        NodeEditor.render(scriptGraph)
    }

    override fun save() {
    }
}

class ImageData(name: String, path: String, open: ImBoolean, val image: DynamicTexture) : FileData(name, path, open) {
    override fun draw() {
        val imageWidth = image.pixels?.width?.toFloat() ?: 1f
        val imageHeight = image.pixels?.height?.toFloat() ?: 1f

        val size = ImGui.getContentRegionMax()

        val scale = min(size.x / imageWidth, size.y / imageHeight)
        val width = imageWidth * scale
        val height = imageHeight * scale

        ImGui.image(image.id, width, height)
    }

    override fun save() {

    }
}