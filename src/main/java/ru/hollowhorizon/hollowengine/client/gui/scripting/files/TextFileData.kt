package ru.hollowhorizon.hollowengine.client.gui.scripting.files

import com.mojang.blaze3d.Blaze3D
import com.mojang.blaze3d.platform.InputConstants
import imgui.ImGui
import imgui.ImVec2
import imgui.extension.texteditor.TextEditor
import imgui.flag.ImGuiCol
import imgui.type.ImBoolean
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import org.lwjgl.glfw.GLFW
import ru.hollowhorizon.hc.client.imgui.Graphics
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
        ImGui.pushStyleColor(ImGuiCol.TextSelectedBg, 1f, 1f, 1f, 1f)

        if (path.startsWith("%")) IDEGui.editor.isReadOnly = true
        if (IDEGui.editor.text.substringBeforeLast('\n') != code) IDEGui.editor.text = code

        IDEGui.currentFile = name
        IDEGui.currentPath = path
        val startPos = ImGui.getCursorScreenPos()

        IDEGui.editor.render("Code Editor")

        if (IDEGui.editor.isTextChanged) IDEGui.editor.text =
            IDEGui.editor.text.substringBeforeLast("\n").replace("\t", "    ")

        val completions = ArrayList(COMPLETIONS)

        val line = IDEGui.editor.currentLineText

        val maxX = (completions.maxOfOrNull { ImGui.calcTextSize(it.toString()).x } ?: 0f) / 2
        val pos = ImVec2(
            (startPos.x + ImGui.calcTextSize(
                line.substring(0, IDEGui.editor.cursorPosition.mColumn.coerceAtMost(line.length))
            ).x - maxX).coerceAtLeast(0f), startPos.y + (IDEGui.editor.cursorPosition.mLine + 1) * ImGui.getFontSize()
        )

        if (openPopup && !(GLFW.GLFW_KEY_SPACE..GLFW.GLFW_KEY_Z).any {
                InputConstants.isKeyDown(
                    Minecraft.getInstance().window.window,
                    it
                )
            }) {
            ImGui.openPopup("##completions")
            openPopup = false
        }
        if(COMPLETIONS.isNotEmpty() && !ImGui.isPopupOpen("##completions")) COMPLETIONS.clear()
        Graphics.popup("##completions") {
            ImGui.setWindowPos(pos.x, pos.y)

            val array = (GLFW.GLFW_KEY_SPACE..GLFW.GLFW_KEY_Z).toMutableList()
            array.add(GLFW.GLFW_KEY_BACKSPACE)

            for (i in array) {
                if (InputConstants.isKeyDown(Minecraft.getInstance().window.window, i)) {
                    ImGui.closeCurrentPopup()
                    if (i != GLFW.GLFW_KEY_BACKSPACE) {
                        val char = if (Screen.hasShiftDown()) Char(i).uppercase()
                        else Char(i).lowercase()
                        IDEGui.editor.insertText(char)
                    } else if (Blaze3D.getTime() - changeTime > 0.5) {
                        changeTime = Blaze3D.getTime()
                        val column = IDEGui.editor.cursorPosition.mColumn
                        val line = IDEGui.editor.cursorPosition.mLine
                        if (column in 0..IDEGui.editor.currentLineText.length) {
                            val text = IDEGui.editor.currentLineText.removeRange((column - 1).coerceAtLeast(0), column)
                            val lines = IDEGui.editor.textLines
                            lines[line] = text
                            IDEGui.editor.textLines = lines
                            IDEGui.editor.setCursorPosition(
                                line,
                                (column - 1).coerceAtLeast(0)
                            )
                        }
                    }

                    break
                }
            }

            completions.forEach {
                if (it.draw() || ((ImGui.isItemFocused() || ImGui.isItemHovered()) && (InputConstants.isKeyDown(
                        Minecraft.getInstance().window.window, GLFW.GLFW_KEY_ENTER
                    ) || InputConstants.isKeyDown(
                        Minecraft.getInstance().window.window, GLFW.GLFW_KEY_TAB
                    )))
                ) {
                    it.complete(IDEGui.editor)
                    ImGui.closeCurrentPopup()
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
        scopeAsync {
            val ext = path.substringAfter('.').substringBeforeLast('.')
            val index = IDEGui.editor.index
            ScriptType.entries.find { it.type == ext }?.let {
                when (it) {
                    ScriptType.NPC_BEHAVIOR -> {
                        currentCodeIndex = index
                        val r = ScriptingCompiler.compileText<NpcBehaviorScript>(code)

                        if (r.errors == null) ScriptCompiledEvent(File(".")).post()
                    }

                    else -> {

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
    if (Minecraft.getInstance().screen != IDEGui) return

    COMPLETIONS.clear()
    COMPLETIONS.addAll(event.completions.distinct())

    if (COMPLETIONS.isNotEmpty()) openPopup = true
}

@SubscribeEvent
fun onScriptError(event: ScriptErrorEvent) {
    if (Minecraft.getInstance().screen != IDEGui) return

    IDEGui.editor.setErrorMarkers(event.error.map { it.line to it.format() }.groupBy { it.first }
        .mapValues { it.value.joinToString("\n") { it.second } })
}

@SubscribeEvent
fun onScriptCompiled(event: ScriptCompiledEvent) {
    if (Minecraft.getInstance().screen != IDEGui) return

    IDEGui.editor.setErrorMarkers(mapOf())
}

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