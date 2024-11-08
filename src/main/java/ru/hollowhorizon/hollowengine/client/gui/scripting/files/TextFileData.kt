package ru.hollowhorizon.hollowengine.client.gui.scripting.files

import com.mojang.blaze3d.platform.InputConstants
import imgui.ImGui
import imgui.ImVec2
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiMouseButton
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
import ru.hollowhorizon.hollowengine.common.scripting.events.EventScript
import ru.hollowhorizon.hollowengine.common.scripting.gui.GuiScript
import ru.hollowhorizon.hollowengine.common.scripting.story.StoryEvent
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.min

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

        ImGui.beginChild(
            "Code Editor",
            ImVec2(),
            false,
            ImGuiWindowFlags.HorizontalScrollbar or ImGuiWindowFlags.NoMove
        )
        if (completionsList.isNotEmpty() && GLFW.glfwGetKey(
                Minecraft.getInstance().window.window,
                GLFW.GLFW_KEY_ESCAPE
            ) == GLFW.GLFW_PRESS
        ) {
            ImGui.setWindowFocus()
        }
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
            val line = IDEGui.editor.currentLineText.substring(0, IDEGui.editor.cursorPosition.mColumn)
            code = IDEGui.editor.text.substringBeforeLast("\n")
            val chars = ('a'..'z') + ('A'..'Z') + ('0'..'9') + '.'
            if (line.lastOrNull() in chars) ActionManager.launchNewAction {
                currentLine = IDEGui.editor.cursorPosition.mLine
                currentColumn = IDEGui.editor.cursorPosition.mColumn
                val extension = name.substringBeforeLast(".").substringAfterLast(".")
                val result = when (extension) {
                    "story" -> ScriptingCompiler.compileText<StoryEvent>(code)
                    "event" -> ScriptingCompiler.compileText<EventScript>(code)
                    "gui" -> ScriptingCompiler.compileText<GuiScript>(code)
                    else -> error("Unknown extension: $extension")
                }
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
    val list = ArrayList(completionsList)

    val line = IDEGui.editor.currentLineText

    val maxX = (list.maxOfOrNull { ImGui.calcTextSize(it.displayText).x } ?: 0f) / 2
    val pos = ImVec2(
        (startPos.x + ImGui.calcTextSize(
            line.substring(0, IDEGui.editor.cursorPosition.mColumn.coerceAtMost(line.length))
        ).x).coerceAtLeast(startPos.x),
        startPos.y + (IDEGui.editor.cursorPosition.mLine + 1) * ImGui.getFontSize() + 5
    )

    ImGui.pushStyleColor(ImGuiCol.NavHighlight, 0)
    ImGui.pushStyleVar(ImGuiStyleVar.ChildRounding, 10f)
    ImGui.pushStyleVar(ImGuiStyleVar.ChildBorderSize, 3f)
    if (list.isNotEmpty()) {
        val sizeX = list.maxOf { ImGui.calcTextSizeX(it.displayText + (if (it.tail == "Unit") "" else it.tail) + " ") } + ImGui.getFontSize() * 2
        ImGui.setCursorScreenPos(pos.x, pos.y)
        ImGui.beginChild(
            "##Completions",
            ImVec2(
                min(
                    ImGui.getContentRegionAvailX(),
                    sizeX
                ),
                (list.size.toFloat() * ImGui.getFontSize() + 25 + if(sizeX > ImGui.getContentRegionAvailX()) 10 else 0)
                    .coerceAtMost(ImGui.getFontSize() * 20f)
            ),
            true,
            ImGuiWindowFlags.HorizontalScrollbar
        )

        list.forEach {
            it.render(IDEGui.editor)
        }
        ImGui.endChild()


        val childPos = ImGui.getItemRectMin()
        val childSize = ImGui.getItemRectSize()

        val mousePos = ImGui.getMousePos()

        val isMouseOutside = mousePos.x < childPos.x || mousePos.x > (childPos.x + childSize.x) ||
                mousePos.y < childPos.y || mousePos.y > (childPos.y + childSize.y)

        if (isMouseOutside && ImGui.isMouseClicked(ImGuiMouseButton.Left)) {
            completionsList.clear()
        }

        for (key in (GLFW.GLFW_KEY_SPACE..GLFW.GLFW_KEY_LAST).reversed()) {
            if(key == GLFW.GLFW_KEY_LEFT_ALT) break
            if(key == GLFW.GLFW_KEY_RIGHT_ALT) break
            if(key == GLFW.GLFW_KEY_TAB) break
            if(key == GLFW.GLFW_KEY_LEFT_SUPER) break
            if(key == GLFW.GLFW_KEY_LEFT_SHIFT) break
            if(key == GLFW.GLFW_KEY_RIGHT_SHIFT) break

            if (GLFW.glfwGetKey(Minecraft.getInstance().window.window, key) == GLFW.GLFW_PRESS) {
                completionsList.clear()
            }
        }
        if(GLFW.glfwGetKey(Minecraft.getInstance().window.window, GLFW.GLFW_KEY_ESCAPE) == GLFW.GLFW_PRESS) {
            completionsList.clear()
        }
        if(GLFW.glfwGetKey(Minecraft.getInstance().window.window, GLFW.GLFW_KEY_BACKSPACE) == GLFW.GLFW_PRESS) {
            completionsList.clear()
        }
    }
    ImGui.popStyleVar(2)
    ImGui.popStyleColor()
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