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
import imgui.ImGuiWindowClass
import imgui.extension.texteditor.TextEditor
import imgui.extension.texteditor.TextEditorLanguageDefinition
import imgui.flag.*
import imgui.type.ImBoolean
import imgui.type.ImInt
import imgui.type.ImString
import kotlinx.serialization.Serializable
import net.minecraft.client.Minecraft
import net.minecraftforge.fml.ModList
import net.minecraftforge.registries.ForgeRegistries
import org.lwjgl.glfw.GLFW
import ru.hollowhorizon.hc.client.handlers.TickHandler
import ru.hollowhorizon.hc.client.imgui.FontAwesomeIcons
import ru.hollowhorizon.hc.client.utils.rl
import ru.hollowhorizon.hc.client.utils.toTexture
import ru.hollowhorizon.hollowengine.client.gui.height
import ru.hollowhorizon.hollowengine.client.gui.width
import ru.hollowhorizon.hollowengine.client.utils.roundTo
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager.toReadablePath
import java.io.File

object CodeEditor {
    val files = HashSet<ScriptData>()
    var currentFile = ""
    var currentPath = ""
    var selectedPath = ""
    val editor = TextEditor().apply {
        setLanguageDefinition(KOTLIN_LANG)

        tabSize = 4
        text = """
            val npc by NPCEntity.creating {
                model = "hollowengine:models/model.gltf"
                textures["default_color_map"] = skin("TheHollowHorizon")
                pos = team.randomPos(5f)
            }

            npc moveTo { team }

            npc lookAt { team.randomPos(5f) }

            npc requestItems {
                +item("minecraft:stone", 10)
                +item("minecraft:dirt", 10)
            }

            npc.name = "Виталик"

            npc say { "Привет, я \$\{npc()!!.name.string}" }
        """.trimIndent()
    }
    var tree = Tree("Загрузка", "null")
    var updateTime = 0
    val input = ImString()
    var inputText = ""
    var inputAction = -1
    var shouldClose = false

    fun draw() {
        if (TickHandler.currentTicks - updateTime > 100) {
            updateTime = TickHandler.currentTicks
            RequestTreePacket().send()
        }

        ImGui.setNextWindowPos(0f, 0f)
        ImGui.setNextWindowSize(width, height)
        val shouldDrawWindowContents = ImGui.begin(
            "CodeEditorSpace",
            ImGuiWindowFlags.NoMove or ImGuiWindowFlags.NoResize or ImGuiWindowFlags.NoCollapse or ImGuiWindowFlags.NoTitleBar
        )
        val dockspaceID = ImGui.getID("MyWindow_DockSpace")
        val workspaceWindowClass = ImGuiWindowClass()
        workspaceWindowClass.setClassId(dockspaceID)
        workspaceWindowClass.dockingAllowUnclassed = false

        if (imgui.internal.ImGui.dockBuilderGetNode(dockspaceID).ptr == 0L) {
            imgui.internal.ImGui.dockBuilderAddNode(
                dockspaceID, imgui.internal.flag.ImGuiDockNodeFlags.DockSpace or
                        imgui.internal.flag.ImGuiDockNodeFlags.NoWindowMenuButton or
                        imgui.internal.flag.ImGuiDockNodeFlags.NoCloseButton
            )
            val region = ImGui.getContentRegionAvail()
            imgui.internal.ImGui.dockBuilderSetNodeSize(dockspaceID, region.x, region.y)

            val leftDockID = ImInt(0)
            val rightDockID = ImInt(0)
            imgui.internal.ImGui.dockBuilderSplitNode(dockspaceID, ImGuiDir.Left, 0.4f, leftDockID, rightDockID);

            val pLeftNode = imgui.internal.ImGui.dockBuilderGetNode(leftDockID.get())
            val pRightNode = imgui.internal.ImGui.dockBuilderGetNode(rightDockID.get())
            pLeftNode.localFlags = pLeftNode.localFlags or imgui.internal.flag.ImGuiDockNodeFlags.NoTabBar or
                    imgui.internal.flag.ImGuiDockNodeFlags.NoDockingSplitMe or imgui.internal.flag.ImGuiDockNodeFlags.NoDockingOverMe or
                    imgui.internal.flag.ImGuiDockNodeFlags.NoTabBar
            pRightNode.localFlags = pRightNode.localFlags or imgui.internal.flag.ImGuiDockNodeFlags.NoTabBar or
                    imgui.internal.flag.ImGuiDockNodeFlags.NoDockingSplitMe or imgui.internal.flag.ImGuiDockNodeFlags.NoDockingOverMe or
                    imgui.internal.flag.ImGuiDockNodeFlags.NoTabBar

            // Dock windows
            imgui.internal.ImGui.dockBuilderDockWindow("File Tree", leftDockID.get())
            imgui.internal.ImGui.dockBuilderDockWindow("Code Editor", rightDockID.get())

            imgui.internal.ImGui.dockBuilderFinish(dockspaceID)
        }

        val dockFlags = if (shouldDrawWindowContents) ImGuiDockNodeFlags.None
        else ImGuiDockNodeFlags.KeepAliveOnly
        val region = ImGui.getContentRegionAvail()
        ImGui.dockSpace(dockspaceID, region.x, region.y, dockFlags, workspaceWindowClass)
        ImGui.end()

        val windowClass = ImGuiWindowClass()
        windowClass.dockNodeFlagsOverrideSet = imgui.internal.flag.ImGuiDockNodeFlags.NoTabBar

        ImGui.setNextWindowClass(windowClass)

        ImGui.begin(
            "File Tree",
            ImGuiWindowFlags.NoMove or ImGuiWindowFlags.NoResize or ImGuiWindowFlags.NoCollapse or ImGuiWindowFlags.NoTitleBar or
                    imgui.internal.flag.ImGuiDockNodeFlags.NoTabBar
        )
        drawTree(tree)
        ImGui.end()

        ImGui.begin(
            "Code Editor",
            ImGuiWindowFlags.NoMove or ImGuiWindowFlags.NoResize or ImGuiWindowFlags.NoCollapse or ImGuiWindowFlags.NoTitleBar
        )

        if (currentPath.endsWith(".kts")) {
            val engine = ModList.get().getModContainerById("hollowengine").get().modInfo
            val compiler = ModList.get().getModContainerById("kotlinscript").get().modInfo
            ImGui.text("Minecraft ${Minecraft.getInstance().game.version.name} | ${engine.displayName} ${engine.version} | ${compiler.displayName} ${compiler.version}")
            ImGui.sameLine()
            ImGui.setCursorPosX(ImGui.getWindowWidth() - 100f)
            if (ImGui.imageButton("hollowengine:textures/gui/play.png".rl.toTexture().id, 32f, 32f)) {
                RunScriptPacket(currentPath).send()
            }
            if (ImGui.isItemHovered()) ImGui.setTooltip("Запустить скрипт")
            ImGui.sameLine()
            if (ImGui.imageButton("hollowengine:textures/gui/stop.png".rl.toTexture().id, 32f, 32f)) {
                StopScriptPacket(currentPath).send()
            }
            if (ImGui.isItemHovered()) ImGui.setTooltip("Остановить скрипт")
        }

        ImGui.beginTabBar("##Files")
        files.forEach { file ->
            if (ImGui.beginTabItem(file.name, file.open, ImGuiTabItemFlags.None)) {
                if (currentFile != file.name) {
                    editor.text = file.code
                }
                currentFile = file.name
                currentPath = file.path
                editor.render("Code Editor")

                if (ImGui.beginDragDropTarget()) {
                    val payload = ImGui.acceptDragDropPayload<Any?>("TREE")
                    if (payload != null) {
                        val data = payload.toString().substringAfter('/').replaceFirst('/', ':')
                        insertAtCursor("\"$data\"")
                    }
                    ImGui.endDragDropTarget()
                }

                if (shouldClose) {
                    ImGui.setKeyboardFocusHere(-1)
                }
                if (editor.isTextChanged) {
                    val text = editor.currentLineText
                    if (editor.cursorPositionColumn - 1 in 0 until text.length) complete(text[editor.cursorPositionColumn - 1])
                    file.code = editor.text.substringBeforeLast("\n")
                    SaveFilePacket(file.path, file.code).send()
                }

                drawScriptPopup()
                if (ImGui.isItemHovered() && ImGui.isMouseClicked(1)) {
                    ImGui.openPopup("ScriptPopup")
                }

                ImGui.endTabItem()
            }
        }
        ImGui.endTabBar()
        ImGui.end()

        drawModalInput()

        if (shouldClose) {
            ImGui.setMouseCursor(0)
            GLFW.glfwSetCursor(
                Minecraft.getInstance().window.window,
                GLFW.glfwCreateStandardCursor(GLFW.GLFW_ARROW_CURSOR)
            )
            shouldClose = false
        }
    }

    fun complete(c: Char) {
        if (ImGui.getIO().getKeysDown(ImGui.getIO().getKeyMap(ImGuiKey.Delete))) return
        if (ImGui.getIO().getKeysDown(ImGui.getIO().getKeyMap(ImGuiKey.Backspace))) return

        val chars = setOf('(', '{', '[', '"')

        val completeChars = arrayOf(')', '}', ']', '"')

        if (c in chars) {
            editor.insertText(completeChars[chars.indexOf(c)].toString())
            editor.setCursorPosition(editor.cursorPositionLine, editor.cursorPositionColumn - 1)
        }
    }

    fun drawTree(tree: Tree) {
        val flags =
            if (tree.drawArrow) ImGuiTreeNodeFlags.SpanFullWidth else ImGuiTreeNodeFlags.NoTreePushOnOpen or ImGuiTreeNodeFlags.Leaf or ImGuiTreeNodeFlags.SpanFullWidth

        drawFolderPopup(tree.path)
        drawFilePopup(tree.path)
        var hovered = false
        var ignore = false
        if (ImGui.treeNodeEx(icon(tree.drawArrow, tree.value.substringAfterLast(".")) + " " + tree.value, flags)) {
            hovered = ImGui.isItemHovered()
            tree.children.forEach { drawTree(it) }

            ignore = true
            if (tree.drawArrow) ImGui.treePop()
        }
        hovered = hovered || (ImGui.isItemHovered() && !ignore)
        if (hovered && ImGui.isMouseClicked(1)) {
            selectedPath = tree.path
            if (tree.drawArrow) ImGui.openPopup("FolderTreePopup##" + tree.path)
            else ImGui.openPopup("FileTreePopup##" + tree.path)
        }
        if ((tree.path.startsWith("assets") || tree.path.startsWith("data")) && !tree.drawArrow && ImGui.beginDragDropSource()) {
            ImGui.setDragDropPayload("TREE", tree.path, ImGuiCond.Once)
            ImGui.pushItemWidth(350f)
            ImGui.text(tree.path.substringAfter('/').replaceFirst('/', ':'))
            ImGui.popItemWidth()
            ImGui.endDragDropSource()
        }

        if (ImGui.isItemActivated() && ImGui.isMouseDoubleClicked(0) && !tree.drawArrow) {
            RequestFilePacket(tree.path).send()
        }
    }

    private fun icon(isFolder: Boolean, ext: String): String {
        return if (isFolder) FontAwesomeIcons.Folder
        else when (ext) {
            "kts" -> FontAwesomeIcons.FileCode
            "json", "txt", "mcfunction", "md" -> FontAwesomeIcons.FileAlt
            "jar", "zip" -> FontAwesomeIcons.FileArchive
            "png", "jpg", "jpeg" -> FontAwesomeIcons.FileImage
            "mp3", "wav", "ogg" -> FontAwesomeIcons.FileAudio
            else -> FontAwesomeIcons.File
        }
    }

    fun drawScriptPopup() {
        val player = Minecraft.getInstance().player ?: return

        if (ImGui.beginPopup("ScriptPopup")) {
            if (ImGui.menuItem(FontAwesomeIcons.Globe + " Вставить ваши координаты")) {
                val loc = player.position()
                val text = "pos(${loc.x.roundTo(2)}, ${loc.y.roundTo(2)}, ${loc.z.roundTo(2)})"
                insertAtCursor(text)
                ImGui.closeCurrentPopup()
            }
            if (ImGui.menuItem(FontAwesomeIcons.Eye + " Вставить координаты взгляда")) {
                val loc = player.pick(100.0, 0.0f, true).location
                val text = "pos(${loc.x.roundTo(2)}, ${loc.y.roundTo(2)}, ${loc.z.roundTo(2)})"
                insertAtCursor(text)
                ImGui.closeCurrentPopup()
            }
            if (ImGui.menuItem(FontAwesomeIcons.HandPaper + " Вставить предмет из вашей руки")) {
                val item = player.getMainHandItem()
                val location = "\"" + ForgeRegistries.ITEMS.getKey(item.item).toString() + "\""
                val count = item.count
                val nbt = if (item.hasTag()) item.getOrCreateTag() else null
                val text = when {
                    nbt == null && count > 1 -> "item($location, $count)"
                    nbt == null && count == 1 -> "item($location)"
                    else -> {
                        "item($location, $count, \"${
                            nbt.toString()
                                .replace("\"", "\\\"")
                        }\")"
                    }
                }
                insertAtCursor(text)
                ImGui.closeCurrentPopup()
            }
            if (ImGui.menuItem(FontAwesomeIcons.Toolbox + " Выбрать предмет из инвентаря")) {
                insertAtCursor("Это сложно, сделаю позже :)")
                ImGui.closeCurrentPopup()
            }
            ImGui.endPopup()
        }
    }

    private fun insertAtCursor(text: String) {
        if (editor.hasSelection()) {
            editor.text = editor.text.substringBeforeLast("\n").replace(editor.selectedText, text)
            editor.setSelectionStart(0, 0)
            editor.setSelectionEnd(0, 0)
        } else editor.insertText(text)
    }

    fun drawFolderPopup(folder: String) {
        if (ImGui.beginPopup("FileTreePopup##$folder")) {
            if (ImGui.menuItem(FontAwesomeIcons.Pen + " Переименовать")) {
                inputAction = 0
                inputText = "Введите новое название скрипта:"
                ImGui.closeCurrentPopup()
            }
            if (ImGui.menuItem(FontAwesomeIcons.TrashAlt + " Удалить")) {
                inputAction = 1
                inputText = "Вы уверены, что хотите\nудалить этот скрипт?"
                ImGui.closeCurrentPopup()
            }
            ImGui.endPopup()
        }
    }

    fun drawFilePopup(file: String) {
        if (ImGui.beginPopup("FolderTreePopup##$file")) {
            if (ImGui.menuItem(FontAwesomeIcons.Folder + " Создать папку")) {
                inputAction = 2
                inputText = "Введите название папки:"
                ImGui.closeCurrentPopup()
            }

            if (ImGui.menuItem(FontAwesomeIcons.FileCode + " Создать Сюжетное события")) {
                inputAction = 3
                inputText = "Введите название скрипта:"
                ImGui.closeCurrentPopup()
            }

            if (ImGui.menuItem(FontAwesomeIcons.FileCode + " Создать Контент-скрипт")) {
                inputAction = 4
                inputText = "Введите название скрипта:"
                ImGui.closeCurrentPopup()
            }
            if (ImGui.menuItem(FontAwesomeIcons.FileCode + " Создать Мод-скрипт")) {
                inputAction = 5
                inputText = "Введите название скрипта:"
                ImGui.closeCurrentPopup()
            }

            if (ImGui.menuItem(FontAwesomeIcons.TrashAlt + " Удалить папку")) {
                inputAction = 6
                inputText = "Вы уверены, что хотите\nудалить эту папку?"
                ImGui.closeCurrentPopup()
            }
            ImGui.endPopup()
        }
    }

    fun drawModalInput() {
        val center = ImGui.getMainViewport().center
        ImGui.setNextWindowPos(center.x, center.y, ImGuiCond.Appearing, 0.5f, 0.5f);

        if (inputAction != -1) {
            ImGui.openPopup("Input")
        }

        if (ImGui.beginPopupModal(
                "Input", ImBoolean(true), ImGuiWindowFlags.AlwaysAutoResize or
                        ImGuiWindowFlags.NoTitleBar
            )
        ) {
            ImGui.text(inputText)
            ImGui.separator()

            if (inputAction == 1 || inputAction == 6) {
                if (ImGui.button("Да", 120f, 0f)) {
                    inputAction = -1
                    files.removeIf { it.path.startsWith(selectedPath) }
                    if (selectedPath.isNotEmpty()) DeleteFilePacket(selectedPath).send()
                    ImGui.closeCurrentPopup()
                    input.set("")
                }
                ImGui.sameLine()
                if (ImGui.button("Отмена", 120f, 0f)) {
                    inputAction = -1
                    ImGui.closeCurrentPopup()
                    input.set("")
                }
            } else {
                ImGui.inputText("##Filename", input)

                if (ImGui.button("OK", 120f, 0f)) {
                    val input = input.get()

                    when (inputAction) {
                        0 -> {
                            RenameFilePacket(selectedPath, input).send()
                            files.removeIf { it.path == selectedPath }
                        }

                        2 -> CreateFilePacket("$selectedPath/$input").send()
                        3 -> CreateFilePacket("$selectedPath/$input.se.kts").send()
                        4 -> CreateFilePacket("$selectedPath/$input.content.kts").send()
                        5 -> CreateFilePacket("$selectedPath/$input.mod.kts").send()
                    }

                    inputAction = -1
                    ImGui.closeCurrentPopup()
                    this.input.set("")
                }
                ImGui.sameLine()
                if (ImGui.button("Отмена", 120f, 0f)) {
                    inputAction = -1
                    ImGui.closeCurrentPopup()
                    input.set("")
                }
            }
            ImGui.endPopup()
        }
    }

    fun tree(file: File): Tree {
        val tree = Tree(file.name, file.toReadablePath())
        tree.drawArrow = file.isDirectory
        file.listFiles()?.sortedBy { if (it.isDirectory) 0 else 1 }?.forEach { tree.children.add(tree(it)) }
        return tree
    }

    fun onClose() {
        files.forEach { SaveFilePacket(it.path, it.code).send() }
    }
}

@Serializable
class Tree(val value: String, val path: String) {
    var drawArrow = true
    val children: MutableList<Tree> = ArrayList()
}

val KOTLIN_LANG = TextEditorLanguageDefinition.c().apply {
    setPreprocChar('@')
    setKeywords(
        arrayOf(
            "break", "continue", "switch", "case", "try",
            "catch", "delete", "do", "while", "else", "finally", "if",
            "else", "for", "is", "as", "in", "instanceof",
            "new", "throw", "typeof", "with", "yield", "when", "return",
            "by", "constructor", "delegate", "dynamic", "field", "get", "set", "init", "value",
            "where", "actual", "annotation", "companion", "field", "external", "infix", "inline", "inner", "internal",
            "open", "operator", "out", "override", "suspend", "vararg",
            "abstract", "extends", "final", "implements", "interface", "super", "throws",
            "data", "class", "fun", "var", "val", "import", "Java", "JSON"
        )
    )

    setName("KotlinScript")

    setSingleLineComment("//")
    setCommentStart("/*")
    setCommentEnd("*/")

    setAutoIdentation(true)
}

