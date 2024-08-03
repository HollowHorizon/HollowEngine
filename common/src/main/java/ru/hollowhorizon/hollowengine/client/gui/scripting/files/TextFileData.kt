package ru.hollowhorizon.hollowengine.client.gui.scripting.files

import com.mojang.blaze3d.Blaze3D
import com.mojang.blaze3d.platform.InputConstants
import imgui.ImGui
import imgui.ImVec2
import imgui.extension.texteditor.TextEditor
import imgui.type.ImBoolean
import net.minecraft.client.Minecraft
import org.lwjgl.glfw.GLFW
import ru.hollowhorizon.hc.client.imgui.ImGuiMethods
import ru.hollowhorizon.hc.common.coroutines.scopeAsync
import ru.hollowhorizon.hc.common.events.SubscribeEvent
import ru.hollowhorizon.hc.common.events.post
import ru.hollowhorizon.hc.common.events.scripting.ScriptCompiledEvent
import ru.hollowhorizon.hc.common.events.scripting.ScriptErrorEvent
import ru.hollowhorizon.hc.common.scripting.ScriptingCompiler
import ru.hollowhorizon.hc.common.scripting.kotlin.CodeCompletionEvent
import ru.hollowhorizon.hc.common.scripting.kotlin.currentCodeIndex
import ru.hollowhorizon.hc.common.scripting.util.CodeCompletion
import ru.hollowhorizon.hollowengine.client.gui.scripting.IDEGui
import ru.hollowhorizon.hollowengine.client.gui.scripting.SaveFilePacket
import ru.hollowhorizon.hollowengine.common.scripting.NpcBehaviorScript
import ru.hollowhorizon.hollowengine.common.scripting.ScriptType
import java.io.File

var changeTime = 0.0

class TextFileData(name: String, path: String, open: ImBoolean, var code: String) : FileData(name, path, open) {
    override fun draw() {
        if (path.startsWith("%")) IDEGui.editor.isReadOnly = true
        if (IDEGui.editor.text.substringBeforeLast('\n') != code) IDEGui.editor.text = code

        IDEGui.currentFile = name
        IDEGui.currentPath = path
        val startPos = ImGui.getCursorScreenPos()

        IDEGui.editor.render("Code Editor")

        val completions = ArrayList(COMPLETIONS)

        val maxX = (completions.maxOfOrNull { ImGui.calcTextSize(it.toString()).x } ?: 0f) / 2
        val pos = ImVec2(
            (startPos.x + ImGui.calcTextSize(
                IDEGui.editor.currentLineText.substring(0, IDEGui.editor.cursorPositionColumn)
            ).x - maxX).coerceAtLeast(0f), startPos.y + (IDEGui.editor.cursorPositionLine + 1) * ImGui.getFontSize()
        )

        if (openPopup) {
            if (Blaze3D.getTime() - changeTime > 1.0) {
                ImGui.openPopup("##completions")
                openPopup = false
            }
        }
        ImGuiMethods.popup("##completions") {
            ImGui.setWindowPos(pos.x, pos.y)

            completions.forEach {
                if (it.draw() || (ImGui.isItemFocused() && (InputConstants.isKeyDown(
                        Minecraft.getInstance().window.window, GLFW.GLFW_KEY_ENTER
                    ) || InputConstants.isKeyDown(
                        Minecraft.getInstance().window.window, GLFW.GLFW_KEY_TAB
                    )))
                ) {
                    it.complete(IDEGui.editor)
                    COMPLETIONS.clear()
                }
                ImGui.separator()
            }
        }

        if (ImGui.beginDragDropTarget()) {
            val payload = ImGui.acceptDragDropPayload<Any?>("TREE")
            if (payload != null) {
                val data = payload.toString().substringAfter('/').replaceFirst('/', ':')
                IDEGui.insertAtCursor("\"$data\"")
            }
            ImGui.endDragDropTarget()
        }

        if (IDEGui.shouldClose) ImGui.setKeyboardFocusHere(-1)

        if (IDEGui.editor.isTextChanged) {
            IDEGui.editor.text = IDEGui.editor.text.substringBeforeLast("\n").replace("\t", "    ")
            openPopup = false
            changeTime = Blaze3D.getTime()

            code = IDEGui.editor.text.substringBeforeLast("\n")
            save()
        }

        IDEGui.drawScriptPopup()
        if (ImGui.isItemHovered() && ImGui.isMouseClicked(1)) ImGui.openPopup("ScriptPopup")

        if (path.startsWith("%")) IDEGui.editor.isReadOnly = false
    }

    override fun save() {
        if (path.startsWith("%")) return
        scopeAsync {
            val ext = path.substringAfter('.').substringBeforeLast('.')
            val index = IDEGui.editor.index
            if (index >= 0 && index < IDEGui.editor.text.length) {
                ScriptType.entries.find { it.type == ext }?.let {
                    when (it) {
                        ScriptType.NPC_BEHAVIOR -> {
                            currentCodeIndex = index
                            val r = ScriptingCompiler.compileText<NpcBehaviorScript>(code)

                            if(r.errors == null) ScriptCompiledEvent(File(".")).post()
                        }

                        else -> {

                        }
                    }
                }
            }
        }
        SaveFilePacket(path, code.toByteArray()).send()
    }
}

var openPopup = false
val COMPLETIONS = ArrayList<CodeCompletion>()

@SubscribeEvent
fun onCompleteEvent(event: CodeCompletionEvent) {
    COMPLETIONS.clear()
    COMPLETIONS.addAll(event.completions.distinct())

    if (COMPLETIONS.isNotEmpty()) openPopup = true
}

@SubscribeEvent
fun onScriptError(event: ScriptErrorEvent) {
    IDEGui.editor.setErrorMarkers(event.error.map { it.line to it.format() }.groupBy { it.first }
        .mapValues { it.value.joinToString("\n") { it.second } })
}

@SubscribeEvent
fun onScriptCompiled(event: ScriptCompiledEvent) {
    IDEGui.editor.setErrorMarkers(mapOf())
}

val TextEditor.index: Int
    get() {
        val line = cursorPositionLine
        val column = cursorPositionColumn
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