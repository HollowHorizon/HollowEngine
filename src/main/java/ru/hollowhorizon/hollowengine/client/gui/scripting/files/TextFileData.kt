package ru.hollowhorizon.hollowengine.client.gui.scripting.files

import imgui.ImGui
import imgui.ImVec2
import imgui.extension.texteditor.TextEditor
import imgui.extension.texteditor.flag.TextEditorPaletteIndex
import imgui.flag.*
import imgui.type.ImBoolean
import kotlinx.coroutines.Job
import net.minecraft.client.Minecraft
import net.minecraft.world.phys.Vec3
import org.jetbrains.kotlin.utils.addToStdlib.ifNotEmpty
import ru.hollowhorizon.hc.client.imgui.Graphics
import ru.hollowhorizon.hc.client.utils.rl
import ru.hollowhorizon.hc.common.coroutines.scopeSync
import ru.hollowhorizon.hollowengine.HollowEngine
import ru.hollowhorizon.hollowengine.client.gui.scripting.IDEGuiV2
import ru.hollowhorizon.hollowengine.client.gui.scripting.KotlinLanguage
import ru.hollowhorizon.hollowengine.client.gui.scripting.SaveFilePacket
import ru.hollowhorizon.hollowengine.client.gui.scripting.insertAtCursor
import ru.hollowhorizon.hollowengine.client.keys.Key
import ru.hollowhorizon.hollowengine.common.commands.roundTo
import ru.hollowhorizon.hollowengine.common.scripting.core.ScriptError
import ru.hollowhorizon.hollowengine.common.scripting.core.ScriptingCompiler
import ru.hollowhorizon.hollowengine.common.scripting.core.completion.CompletionVariant
import ru.hollowhorizon.hollowengine.common.scripting.events.EventScript
import ru.hollowhorizon.hollowengine.common.scripting.gui.GuiScript
import ru.hollowhorizon.hollowengine.common.scripting.story.StoryEvent
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.max
import kotlin.math.min

var currentLine = 0
var currentColumn = 0
val completionsList = ArrayList<CompletionVariant>()

class TextFileData(project: IDEGuiV2, name: String, path: String, open: ImBoolean, private var code: String) :
    FileData(project, name, path, open) {
    val textEditor = TextEditor().apply {
        isImGuiChildIgnored = true
        languageDefinition = KotlinLanguage

        tabSize = HollowEngine.config.ideConfig.tabSpace
        text = code
        val palette = palette
        palette[TextEditorPaletteIndex.Background] = 0
        palette[TextEditorPaletteIndex.CurrentLineEdge] = 0xFF2E2826.toInt()
        palette[TextEditorPaletteIndex.CurrentLineFill] = 0x882E2826.toInt()
        palette[TextEditorPaletteIndex.CurrentLineFillInactive] = 0x882E2826.toInt()
        palette[TextEditorPaletteIndex.LineNumber] = 0xFF59504B.toInt()
        palette[TextEditorPaletteIndex.Number] = 0xFFADAB29.toInt()
        palette[TextEditorPaletteIndex.Keyword] = 0xFF6D8ECF.toInt()
        palette[TextEditorPaletteIndex.String] = 0xFF73AB6A.toInt()
        palette[TextEditorPaletteIndex.Comment] = 0xFF857E7A.toInt()
        this.palette = palette
    }
    var fileErrors = emptyList<ScriptError>()
    var oldScroll = 0f

    override fun draw() {
        val isFileFocused = ImGui.isWindowFocused(ImGuiFocusedFlags.ChildWindows)

        ImGui.pushStyleColor(ImGuiCol.TextSelectedBg, 1f, 1f, 1f, 1f)
        ImGui.pushStyleColor(ImGuiCol.ChildBg, 0f, 0f, 0f, 0f)

        val startPos = ImGui.getCursorScreenPos()

        ImGui.beginChild(
            filePath,
            ImVec2(),
            false,
            ImGuiWindowFlags.HorizontalScrollbar or ImGuiWindowFlags.NoMove
        )
        if (isFileFocused && completionsList.isNotEmpty() && Key.ESCAPE.isReleased()) {
            ImGui.setWindowFocus()
        }

        textEditor.render("Code Editor")
        if(oldScroll != -1f) {
            ImGui.setScrollY(oldScroll)
            oldScroll = -1f
        }

        if (isFileFocused) drawCompletions(textEditor, startPos)
        if (fileErrors.isNotEmpty()) drawErrors(textEditor, fileErrors, startPos)

        if (textEditor.isTextChanged) {
            textEditor.text = textEditor.text.substringBeforeLast("\n").replace("\t", " ".repeat(HollowEngine.config.ideConfig.tabSpace))
            oldScroll = ImGui.getScrollY()
        }

        ImGui.endChild()
        if(ImGui.isItemHovered() && ImGui.isMouseClicked(ImGuiMouseButton.Right)) ImGui.openPopup("CodeActions##$filePath")

        if (ImGui.beginDragDropTarget()) {
            val payload = ImGui.acceptDragDropPayload<Any?>("TREE")
            if (payload != null) {
                val data = payload.toString().substringAfter('/').replaceFirst('/', ':')
                textEditor.insertAtCursor("\"$data\"")
            }
            ImGui.endDragDropTarget()
        }

        if (textEditor.isTextChanged) {
            code = textEditor.text.substringBeforeLast("\n")
            if(fileName.substringAfterLast('.') == "kts") ActionManager.launchNewAction {
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
                    fileErrors = this
                }
            }
            save()
        }

        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 10f, 10f)
        if(ImGui.beginPopup("CodeActions##$filePath")) {
            val iconSize = ImGui.getFontSize().toFloat()
            Graphics.image("hollowengine:textures/gui/icons/person.png".rl, iconSize, iconSize)
            ImGui.sameLine()
            if(ImGui.menuItem("Вставить мои координаты")) {
                val loc = Minecraft.getInstance().player?.position() ?: Vec3.ZERO
                textEditor.insertAtCursor("pos(${loc.x.roundTo(2)}, ${loc.y.roundTo(2)}, ${loc.z.roundTo(2)})")
                ImGui.closeCurrentPopup()
            }

            Graphics.image("hollowengine:textures/gui/icons/person_b.png".rl, iconSize, iconSize)
            ImGui.sameLine()
            if(ImGui.menuItem("Вставить координаты взгляда")) {
                val loc = Minecraft.getInstance().player?.pick(10.0, 0f, false)?.location ?: Vec3.ZERO
                textEditor.insertAtCursor("pos(${loc.x.roundTo(2)}, ${loc.y.roundTo(2)}, ${loc.z.roundTo(2)})")
                ImGui.closeCurrentPopup()
            }

            ImGui.endPopup()
        }
        ImGui.popStyleVar()

        ImGui.popStyleColor(2)
    }

    override fun save() {
        if (filePath.startsWith("%")) return
        SaveFilePacket(filePath, code.toByteArray()).send()
    }
}

fun drawErrors(textEditor: TextEditor, fileErrors: List<ScriptError>, startPos: ImVec2) {
    val mLine = textEditor.totalLines


    fileErrors.forEach {
        if(it.severity != ScriptError.Severity.ERROR && it.severity != ScriptError.Severity.FATAL) return@forEach
        if(it.line > textEditor.totalLines) return@forEach

        val column = (it.column - 1).coerceAtLeast(0)

        val line = textEditor.textLines[it.line-1]
        val lineWidth = ImGui.calcTextSizeX(line.substring(0, column.coerceAtMost(line.length)))
        val nextCharSize = if (column + 1 <= line.length) ImGui.calcTextSizeX(
            line.substring(
                column,
                column + 1
            )
        ) else ImGui.getFontSize().toFloat()

        val pos = ImVec2(
            startPos.x + ImGui.calcTextSizeX("$mLine  ") + 10 + lineWidth,
            startPos.y + (it.line) * ImGui.getFontSize() - ImGui.getScrollY()
        )

        drawZigZagLine(pos, pos.clone() + ImVec2(nextCharSize, 0f),
            (nextCharSize / 3).toInt(), 5f, 0xFF0000FF.toInt(), 3f)

        if (ImGui.isMouseHoveringRect(pos.clone() - ImVec2(0f, 30f), pos.clone() + ImVec2(nextCharSize, 4f))) {
            ImGui.beginTooltip()
            Graphics.textShadow(it.toString())
            ImGui.endTooltip()
        }
    }
}

fun drawZigZagLine(start: ImVec2, end: ImVec2, segments: Int, amplitude: Float, color: Int, thickness: Float) {
    val drawList = ImGui.getWindowDrawList()
    val segmentLength = (end.x - start.x) / segments

    var x = start.x
    var y = start.y
    var direction = 1

    for (i in 0 until segments) {
        val nextX = x + segmentLength
        val nextY = y + direction * amplitude

        drawList.addLine(ImVec2(x, y), ImVec2(nextX, nextY), color, thickness)

        x = nextX
        y = nextY
        direction = -direction // Меняем направление для зигзага
    }

    // Соединяем последнюю точку с конечной позицией
    drawList.addLine(ImVec2(x, y), ImVec2(end.x, end.y), color, thickness)
}

fun drawCompletions(textEditor: TextEditor, startPos: ImVec2) {
    val list = ArrayList(completionsList)

    val windowWidth = ImGui.getWindowWidth()
    val line = textEditor.currentLineText
    val sizeX = min(
        (list.maxOfOrNull { it.textWidth } ?: 0f) + ImGui.getStyle().framePaddingX * 2 + ImGui.getStyle().itemSpacingX,
        windowWidth
    )

    val mLine = textEditor.cursorPosition.mLine
    val mColumn = textEditor.cursorPosition.mColumn
    if (mColumn > line.length) return
    val startText = line.substring(0, mColumn)
    var charPos = startText.lastIndexOf('.') + 1
    sequenceOf('(', '[', '{', ' ').forEach {
        charPos = max(charPos, startText.lastIndexOf(it) + 1)
    }
    charPos = charPos.coerceAtLeast(0)

    val lineWidth = ImGui.calcTextSizeX(startText.substring(0, charPos))

    val pos = ImVec2(
        (startPos.x + lineWidth + ImGui.calcTextSizeX(mLine.toString())).coerceAtLeast(windowWidth - sizeX)
            .coerceAtLeast(startPos.x) + 10,
        startPos.y + (textEditor.cursorPosition.mLine + 1) * ImGui.getFontSize() + 5 - ImGui.getScrollY()
    )

    ImGui.pushStyleColor(ImGuiCol.NavHighlight, 0)
    ImGui.pushStyleColor(ImGuiCol.Border, 0xFF4A4543.toInt())
    ImGui.pushStyleColor(ImGuiCol.ChildBg, 0xFF302D2B.toInt())
    ImGui.pushStyleVar(ImGuiStyleVar.ChildRounding, 10f)
    ImGui.pushStyleVar(ImGuiStyleVar.ChildBorderSize, 3f)
    if (list.isNotEmpty()) {
        ImGui.setCursorScreenPos(pos.x, pos.y)
        ImGui.beginChild(
            "##Completions",
            ImVec2(
                min(
                    ImGui.getWindowWidth(),
                    sizeX
                ),
                (list.size.toFloat() * ImGui.getFontSize() + 25 + if (sizeX > ImGui.getContentRegionAvailX()) 10 else 0)
                    .coerceAtMost(ImGui.getFontSize() * 20f)
            ),
            true
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

        val keys = listOf(
            Key.LEFT_ALT, Key.RIGHT_ALT, Key.TAB,
            Key.LEFT_SUPER, Key.RIGHT_SUPER, Key.LEFT_ALT, Key.RIGHT_ALT,
            Key.LEFT_SHIFT, Key.RIGHT_SHIFT, Key.LEFT_CONTROL, Key.RIGHT_CONTROL,
        )
        if ((Key.ESCAPE.isPressed() || Key.BACKSPACE.isPressed() || !keys.any { it.isPressed() }) && Key.entries.any { it.isPressed() }) {
            completionsList.clear()
        }
    }
    ImGui.popStyleVar(2)
    ImGui.popStyleColor(3)
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