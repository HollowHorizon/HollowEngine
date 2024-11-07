package ru.hollowhorizon.hollowengine.client.gui.scripting.files

import com.mojang.blaze3d.platform.InputConstants
import imgui.ImGui
import imgui.ImVec2
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiStyleVar
import imgui.flag.ImGuiWindowFlags
import imgui.type.ImBoolean
import kotlinx.coroutines.Job
import net.minecraft.client.Minecraft
import org.lwjgl.glfw.GLFW
import ru.hollowhorizon.hc.common.coroutines.scopeSync
import ru.hollowhorizon.hollowengine.client.gui.scripting.IDEGui
import ru.hollowhorizon.hollowengine.client.gui.scripting.SaveFilePacket
import ru.hollowhorizon.hollowengine.common.scripting.core.ScriptingCompiler
import ru.hollowhorizon.hollowengine.common.scripting.core.completion.CompletionVariant
import ru.hollowhorizon.hollowengine.common.scripting.story.StoryEvent
import kotlin.coroutines.cancellation.CancellationException

var currentLine = 0
var currentColumn = 0
val completionsList = ArrayList<CompletionVariant>()

class TextFileData(name: String, path: String, open: ImBoolean, var code: String) : FileData(name, path, open) {
    override fun draw() {
        ImGui.pushStyleColor(ImGuiCol.TextSelectedBg, 1f, 1f, 1f, 1f)

        if (path.startsWith("%")) IDEGui.editor.isReadOnly = true
        if (IDEGui.editor.text.substringBeforeLast('\n') != code) IDEGui.editor.text = code

        IDEGui.currentFile = name
        IDEGui.currentPath = path
        val startPos = ImGui.getCursorScreenPos()

        ImGui.beginChild("Code Editor", ImVec2(), false, ImGuiWindowFlags.HorizontalScrollbar or ImGuiWindowFlags.NoMove)
        IDEGui.editor.render("Code Editor")

        drawCompletions(startPos)

        ImGui.endChild()

        if (IDEGui.editor.isTextChanged) IDEGui.editor.text =
            IDEGui.editor.text.substringBeforeLast("\n").replace("\t", "    ")


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
            ActionManager.launchNewAction {
                currentLine = IDEGui.editor.cursorPosition.mLine
                currentColumn = IDEGui.editor.cursorPosition.mColumn
                completionsList.clear()
                ScriptingCompiler.compileText<StoryEvent>(code)
            }
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

fun drawCompletions(startPos: ImVec2) {
    val completionsList = ArrayList(completionsList)

    val line = IDEGui.editor.currentLineText

    val maxX = (completionsList.maxOfOrNull { ImGui.calcTextSize(it.displayText).x } ?: 0f) / 2
    val pos = ImVec2(
        (startPos.x + ImGui.calcTextSize(
            line.substring(0, IDEGui.editor.cursorPosition.mColumn.coerceAtMost(line.length))
        ).x - maxX).coerceAtLeast(startPos.x), startPos.y + (IDEGui.editor.cursorPosition.mLine + 1) * ImGui.getFontSize() + 5
    )

    ImGui.pushStyleVar(ImGuiStyleVar.ChildRounding, 10f)
    ImGui.pushStyleVar(ImGuiStyleVar.ChildBorderSize, 3f)
    if (completionsList.isNotEmpty()) {
        ImGui.setCursorScreenPos(pos.x, pos.y)
        ImGui.beginChild("##Completions", ImVec2(ImGui.getContentRegionAvailX(), (ImGui.getFontSize() * 10f).coerceAtMost((completionsList.size.toFloat()+1) * ImGui.getFontSize())), true, ImGuiWindowFlags.NoNavFocus)
        completionsList.forEach {
            it.render(IDEGui.editor)
        }
        ImGui.endChild()
    }
    ImGui.popStyleVar(2)
}

var openPopup = false

object ActionManager {
    private var currentJob: Job? = null

    fun launchNewAction(action: suspend () -> Unit) {
        currentJob?.cancel()

        currentJob = scopeSync {
            try {
                action()
            } catch (e: CancellationException) {
                // Обработка отмены корутины (если нужно)
            }
        }
    }
}