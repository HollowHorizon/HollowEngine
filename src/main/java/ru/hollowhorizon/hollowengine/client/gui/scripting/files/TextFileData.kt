package ru.hollowhorizon.hollowengine.client.gui.scripting.files

import imgui.ImGui
import imgui.ImVec2
import imgui.ImVec4
import imgui.extension.texteditor.TextEditor
import imgui.extension.texteditor.flag.TextEditorPaletteIndex
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiMouseButton
import imgui.flag.ImGuiStyleVar
import imgui.flag.ImGuiWindowFlags
import imgui.type.ImBoolean
import kotlinx.coroutines.Job
import net.minecraft.client.Minecraft
import org.jetbrains.kotlin.utils.addToStdlib.ifNotEmpty
import org.lwjgl.glfw.GLFW
import ru.hollowhorizon.hc.client.imgui.Graphics.color
import ru.hollowhorizon.hc.common.coroutines.scopeSync
import ru.hollowhorizon.hollowengine.HollowEngine
import ru.hollowhorizon.hollowengine.client.gui.scripting.IDEGuiV2
import ru.hollowhorizon.hollowengine.client.gui.scripting.KotlinLanguage
import ru.hollowhorizon.hollowengine.client.gui.scripting.SaveFilePacket
import ru.hollowhorizon.hollowengine.client.gui.scripting.insertAtCursor
import ru.hollowhorizon.hollowengine.client.keys.Key
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
private val COMPLETION_CHARS = ('a'..'z') + ('A'..'Z') + ('0'..'9') + '.'

class TextFileData(project: IDEGuiV2, name: String, path: String, open: ImBoolean, private var code: String) :
    FileData(project, name, path, open) {
    private val textEditor = TextEditor().apply {
        isImGuiChildIgnored = true
        languageDefinition = KotlinLanguage

        tabSize = HollowEngine.config.ideConfig.tabSpace
        text = code
        palette[TextEditorPaletteIndex.Background] = 0
        palette[TextEditorPaletteIndex.CurrentLineEdge] = ImVec4(1f, 1f, 1f, 1f).color
    }

    override fun draw() {
        val isThatFile = project.currentFile == project.files.indexOf(this)

        ImGui.pushStyleColor(ImGuiCol.TextSelectedBg, 1f, 1f, 1f, 1f)
        ImGui.pushStyleColor(ImGuiCol.ChildBg, 0f, 0f, 0f, 0f)

        project.currentFile = project.files.indexOf(this)
        val startPos = ImGui.getCursorScreenPos()

        ImGui.beginChild(
            filePath,
            ImVec2(),
            false,
            ImGuiWindowFlags.HorizontalScrollbar or ImGuiWindowFlags.NoMove
        )
        if (isThatFile && completionsList.isNotEmpty() && Key.ESCAPE.isReleased()) {
            ImGui.setWindowFocus()
        }
        textEditor.render("Code Editor")

        if(isThatFile) drawCompletions(textEditor, startPos)

        ImGui.endChild()

        if (textEditor.isTextChanged) textEditor.text =
            textEditor.text.substringBeforeLast("\n").replace("\t", "    ")

        if (ImGui.beginDragDropTarget()) {
            val payload = ImGui.acceptDragDropPayload<Any?>("TREE")
            if (payload != null) {
                val data = payload.toString().substringAfter('/').replaceFirst('/', ':')
                textEditor.insertAtCursor("\"$data\"")
            }
            ImGui.endDragDropTarget()
        }

        if (textEditor.isTextChanged) {
            val line = textEditor.currentLineText.substring(0, textEditor.cursorPosition.mColumn)
            code = textEditor.text.substringBeforeLast("\n")
            if (line.lastOrNull() in COMPLETION_CHARS) ActionManager.launchNewAction {
                currentLine = textEditor.cursorPosition.mLine
                currentColumn = textEditor.cursorPosition.mColumn
                val extension = fileName.substringBeforeLast(".").substringAfterLast(".")
                val result = when (extension) {
                    "story" -> ScriptingCompiler.compileText<StoryEvent>(code)
                    "event" -> ScriptingCompiler.compileText<EventScript>(code)
                    "gui" -> ScriptingCompiler.compileText<GuiScript>(code)
                    else -> error("Unknown extension: $extension")
                }
                result.errors?.ifNotEmpty {
                    project.fileErrors = this
                }
            }
            save()
        }

        ImGui.popStyleColor(2)
    }

    override fun save() {
        if (filePath.startsWith("%")) return
        SaveFilePacket(filePath, code.toByteArray()).send()
    }
}

fun drawCompletions(textEditor: TextEditor, startPos: ImVec2) {
    val list = ArrayList(completionsList)

    val line = textEditor.currentLineText

    val maxX = (list.maxOfOrNull { ImGui.calcTextSize(it.displayText).x } ?: 0f) / 2
    val pos = ImVec2(
        (startPos.x + ImGui.calcTextSize(
            line.substring(0, textEditor.cursorPosition.mColumn.coerceAtMost(line.length))
        ).x).coerceAtLeast(startPos.x),
        startPos.y + (textEditor.cursorPosition.mLine + 1) * ImGui.getFontSize() + 5
    )

    ImGui.pushStyleColor(ImGuiCol.NavHighlight, 0)
    ImGui.pushStyleColor(ImGuiCol.ChildBg, 0)
    ImGui.pushStyleVar(ImGuiStyleVar.ChildRounding, 10f)
    ImGui.pushStyleVar(ImGuiStyleVar.ChildBorderSize, 3f)
    if (list.isNotEmpty()) {
        val sizeX =
            list.maxOf { ImGui.calcTextSizeX(it.displayText + (if (it.tail == "Unit") "" else it.tail) + " ") } + ImGui.getFontSize() * 2
        ImGui.setCursorScreenPos(pos.x, pos.y)
        ImGui.beginChild(
            "##Completions",
            ImVec2(
                min(
                    ImGui.getContentRegionAvailX(),
                    sizeX
                ),
                (list.size.toFloat() * ImGui.getFontSize() + 25 + if (sizeX > ImGui.getContentRegionAvailX()) 10 else 0)
                    .coerceAtMost(ImGui.getFontSize() * 20f)
            ),
            true,
            ImGuiWindowFlags.HorizontalScrollbar
        )

        list.forEach { it.render(textEditor) }
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
            if (key == GLFW.GLFW_KEY_LEFT_ALT) break
            if (key == GLFW.GLFW_KEY_RIGHT_ALT) break
            if (key == GLFW.GLFW_KEY_TAB) break
            if (key == GLFW.GLFW_KEY_LEFT_SUPER) break
            if (key == GLFW.GLFW_KEY_LEFT_SHIFT) break
            if (key == GLFW.GLFW_KEY_RIGHT_SHIFT) break

            if (GLFW.glfwGetKey(Minecraft.getInstance().window.window, key) == GLFW.GLFW_PRESS) {
                completionsList.clear()
            }
        }
        if (GLFW.glfwGetKey(Minecraft.getInstance().window.window, GLFW.GLFW_KEY_ESCAPE) == GLFW.GLFW_PRESS) {
            completionsList.clear()
        }
        if (GLFW.glfwGetKey(Minecraft.getInstance().window.window, GLFW.GLFW_KEY_BACKSPACE) == GLFW.GLFW_PRESS) {
            completionsList.clear()
        }
    }
    ImGui.popStyleVar(2)
    ImGui.popStyleColor(2)
}

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