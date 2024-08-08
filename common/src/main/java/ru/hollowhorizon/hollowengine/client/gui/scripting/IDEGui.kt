package ru.hollowhorizon.hollowengine.client.gui.scripting

import com.mojang.blaze3d.platform.NativeImage
import imgui.ImGui
import imgui.ImGuiWindowClass
import imgui.ImVec2
import imgui.extension.texteditor.TextEditor
import imgui.extension.texteditor.TextEditorLanguageDefinition
import imgui.flag.*
import imgui.type.ImBoolean
import imgui.type.ImInt
import imgui.type.ImString
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.resources.ResourceManager
import org.lwjgl.glfw.GLFW
import ru.hollowhorizon.hc.client.imgui.FontAwesomeIcons
import ru.hollowhorizon.hc.client.imgui.ImGuiHandler
import ru.hollowhorizon.hc.client.utils.*
import ru.hollowhorizon.hc.common.coroutines.onMainThreadSync
import ru.hollowhorizon.hc.common.coroutines.scopeSync
import ru.hollowhorizon.hc.common.events.SubscribeEvent
import ru.hollowhorizon.hc.common.events.registry.RegisterKeyBindingsEvent
import ru.hollowhorizon.hc.common.events.tick.TickEvent
import ru.hollowhorizon.hc.common.network.request
import ru.hollowhorizon.hollowengine.HollowEngine.MODID
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.COMPLETIONS
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.FileData
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.ImageFileData
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.TextFileData
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager.toReadablePath
import java.io.File
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

object IDEGui : Screen("".literal) {
    val files = HashSet<FileData>()
    var currentFile = ""
    var currentPath = ""
    var selectedPath = ""
    val editor = TextEditor().apply {
        setLanguageDefinition(KOTLIN_LANG)

        tabSize = 4
        text = ""
    }
    var tree = Tree("codeEditor.$MODID.loading".mcTranslate.string, "null")
    val input = ImString()
    var inputText = ""
    var inputAction = -1
    var shouldClose = false
    var loadSettings = true

    override fun init() {
        super.init()
        scopeSync {
            onMainThreadSync {
                reloadTree()
            }
        }
    }

    suspend fun reloadTree() {
        tree = RequestTreePacket(Tree("", "")).request().tree.apply {
            children.find { it.value == "Моды" }?.children?.add(
                0,
                Minecraft.getInstance().resourceManager.toClientTree()
            )

            // Ассеты должны быть клиентские
            children.find { it.value == "hollowengine" }?.children?.apply {
                removeIf { it.value == "assets" }
                add(tree(DirectoryManager.HOLLOW_ENGINE.resolve("assets").toFile()))
            }
        }
        tree.sort()
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        val file = DirectoryManager.HOLLOW_ENGINE.resolve(".gui_cache/code_editor.ini").toFile()
        if (!file.parentFile.exists()) file.parentFile.mkdirs()
        if (!file.exists()) file.createNewFile()
        ImGuiHandler.drawFrame {
            if (loadSettings) {
                loadSettings = false
                ImGui.loadIniSettingsFromMemory(file.readText())
            }
            draw()
            file.writeText(ImGui.saveIniSettingsToMemory())

            if (shouldClose) onClose()
        }
    }

    override fun onClose() {
        if (!shouldClose) {
            shouldClose = true
            return
        }

        super.onClose()
        files.forEach { it.save() }
        shouldClose = false
    }

    override fun isPauseScreen() = false

    private fun draw() {

        val window = Minecraft.getInstance().window
        ImGui.setNextWindowPos(0f, 0f)
        ImGui.setNextWindowSize(window.width.toFloat(), window.height.toFloat())
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
        tree.children.forEach {
            drawTree(it)
        }
        ImGui.end()

        ImGui.begin(
            "Code Editor",
            ImGuiWindowFlags.NoMove or ImGuiWindowFlags.NoResize or ImGuiWindowFlags.NoCollapse or ImGuiWindowFlags.NoTitleBar
        )

        if (currentPath.endsWith(".kts") && files.isNotEmpty()) {
            ImGui.text("Minecraft 1.21 | Fabric | HollowCore Compiler 2.0")
        }

        ImGui.beginTabBar("##Files")
        files.removeIf { file ->
            val lastOpen = file.open.get()
            if (ImGui.beginTabItem(file.name, file.open, ImGuiTabItemFlags.None)) {
                file.draw()
                ImGui.endTabItem()
            }
            lastOpen && !file.open.get()
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
            files.find { it.path == tree.path && it is ImageFileData }?.let { data ->
                val image = (data as ImageFileData).image

                val imageWidth = image.pixels?.width?.toFloat() ?: 1f
                val imageHeight = image.pixels?.height?.toFloat() ?: 1f
                val size = ImVec2(350f, 350f)

                val scale = min(size.x / imageWidth, size.y / imageHeight)
                val width = imageWidth * scale
                val height = imageHeight * scale

                ImGui.image(image.id, width, height)
            }
            ImGui.popItemWidth()
            ImGui.endDragDropSource()
        }

        if (ImGui.isItemActivated() && ImGui.isMouseDoubleClicked(0) && !tree.drawArrow) {
            if (tree.path.startsWith("%assets%:")) {
                files.removeIf { it.path == tree.path }
                if (tree.path.endsWith(".png")) {
                    val img = NativeImage.read(tree.path.substring(9).rl.stream)
                    val texture = DynamicTexture(img)
                    files.add(
                        ImageFileData(
                            tree.value,
                            tree.path,
                            ImBoolean(true),
                            texture
                        )
                    )
                } else {
                    files.add(
                        TextFileData(
                            tree.value,
                            tree.path,
                            ImBoolean(true),
                            String(tree.path.substring(9).rl.stream.readBytes())
                        )
                    )
                }
            } else {
                RequestFilePacket(tree.path).send()
            }
        }
    }

    private fun icon(isFolder: Boolean, ext: String): String {
        return if (isFolder) {
            when (ext) {
                "fabric", "forge", "c" -> FontAwesomeIcons.Cogs
                "assets" -> FontAwesomeIcons.Images
                "data" -> FontAwesomeIcons.Database
                "minecraft" -> FontAwesomeIcons.Cube
                else -> FontAwesomeIcons.Folder
            }
        } else when (ext) {
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
            if (ImGui.menuItem(FontAwesomeIcons.Globe + " " + "codeEditor.$MODID.insert.pos".mcTranslate.string)) {
                val loc = player.position()
                val text = "pos(${loc.x.roundTo(2)}, ${loc.y.roundTo(2)}, ${loc.z.roundTo(2)})"
                insertAtCursor(text)
                ImGui.closeCurrentPopup()
            }
            if (ImGui.menuItem(FontAwesomeIcons.Eye + " " + "codeEditor.$MODID.insert.look".mcTranslate.string)) {
                val loc = player.pick(100.0, 0.0f, true).location
                val text = "pos(${loc.x.roundTo(2)}, ${loc.y.roundTo(2)}, ${loc.z.roundTo(2)})"
                insertAtCursor(text)
                ImGui.closeCurrentPopup()
            }
            if (ImGui.menuItem(FontAwesomeIcons.HandPaper + " " + "codeEditor.$MODID.insert.itemInHand".mcTranslate.string)) {
                val item = player.mainHandItem
                val location = "\"" + BuiltInRegistries.ITEM.getKey(item.item).toString() + "\""
                val count = item.count
                val text = when {
                    count > 1 -> "item($location, $count)"
                    else -> "item($location)"
                }
                insertAtCursor(text)
                ImGui.closeCurrentPopup()
            }
            if (ImGui.menuItem(FontAwesomeIcons.Toolbox + " " + "codeEditor.$MODID.insert.itemFromInv".mcTranslate.string)) {
                insertAtCursor("codeEditor.$MODID.inNewVersion".mcTranslate.string)
                ImGui.closeCurrentPopup()
            }
            ImGui.endPopup()
        }
    }

    fun insertAtCursor(text: String) {
        if (editor.hasSelection()) {
            editor.text = editor.text.substringBeforeLast("\n").replace(editor.selectedText, text)
            editor.setSelectionStart(0, 0)
            editor.setSelectionEnd(0, 0)
        } else editor.insertText(text)
    }

    fun drawFolderPopup(folder: String) {
        if (ImGui.beginPopup("FileTreePopup##$folder")) {
            if (ImGui.menuItem(FontAwesomeIcons.Pen + " " + "codeEditor.$MODID.rename".mcTranslate.string)) {
                inputAction = 0
                inputText = "codeEditor.$MODID.rename.new".mcTranslate.string + ":"
                ImGui.closeCurrentPopup()
            }
            if (ImGui.menuItem(FontAwesomeIcons.TrashAlt + " " + "codeEditor.$MODID.delete".mcTranslate.string)) {
                inputAction = 1
                inputText =
                    "codeEditor.$MODID.delete.warning".mcTranslate.string + "\n" + "codeEditor.$MODID.delete.warning.script".mcTranslate.string + "?"
                ImGui.closeCurrentPopup()
            }
            ImGui.endPopup()
        }
    }

    fun drawFilePopup(file: String) {
        if (ImGui.beginPopup("FolderTreePopup##$file")) {
            if (ImGui.menuItem(FontAwesomeIcons.Folder + " " + "codeEditor.$MODID.create".mcTranslate.string + " " + "codeEditor.$MODID.create.folder".mcTranslate.string)) {
                inputAction = 2
                inputText =
                    "codeEditor.hollowengine.enter".mcTranslate.string + " " + "codeEditor.hollowengine.enter.directory".mcTranslate.string + ":"
                ImGui.closeCurrentPopup()
            }

            if (ImGui.menuItem(FontAwesomeIcons.FileCode + " " + "codeEditor.$MODID.create".mcTranslate.string + " " + "codeEditor.$MODID.create.story".mcTranslate.string)) {
                inputAction = 3
                inputText =
                    "codeEditor.hollowengine.enter".mcTranslate.string + " " + "codeEditor.hollowengine.enter.script".mcTranslate.string + ":"
                ImGui.closeCurrentPopup()
            }

            if (ImGui.menuItem(FontAwesomeIcons.FileCode + " " + "codeEditor.$MODID.create".mcTranslate.string + " " + "codeEditor.$MODID.create.content".mcTranslate.string)) {
                inputAction = 4
                inputText =
                    "codeEditor.hollowengine.enter".mcTranslate.string + " " + "codeEditor.hollowengine.enter.script".mcTranslate.string + ":"
                ImGui.closeCurrentPopup()
            }
            if (ImGui.menuItem(FontAwesomeIcons.FileCode + " " + "codeEditor.$MODID.create".mcTranslate.string + " " + "codeEditor.$MODID.create.mod".mcTranslate.string)) {
                inputAction = 5
                inputText =
                    "codeEditor.hollowengine.enter".mcTranslate.string + " " + "codeEditor.hollowengine.enter.script".mcTranslate.string + ":"
                ImGui.closeCurrentPopup()
            }

            if (ImGui.menuItem(FontAwesomeIcons.TrashAlt + " " + "codeEditor.$MODID.delete.dir".mcTranslate.string)) {
                inputAction = 6
                inputText =
                    "codeEditor.$MODID.delete.warning".mcTranslate.string + "\n" + "codeEditor.$MODID.delete.warning.dir".mcTranslate.string + "?"
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
                if (ImGui.button("codeEditor.$MODID.yes".mcTranslate.string, 120f, 0f)) {
                    inputAction = -1
                    files.removeIf { it.path.startsWith(selectedPath) }
                    if (selectedPath.isNotEmpty()) DeleteFilePacket(selectedPath).send()
                    scopeSync {
                        delay(100L)
                        tree = RequestTreePacket(Tree("", "")).request().tree
                    }
                    ImGui.closeCurrentPopup()
                    input.set("")
                }
                ImGui.sameLine()
                if (ImGui.button("codeEditor.$MODID.no".mcTranslate.string, 120f, 0f)) {
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

                    scopeSync {
                        delay(100L)
                        tree = RequestTreePacket(Tree("", "")).request().tree
                    }

                    inputAction = -1
                    ImGui.closeCurrentPopup()
                    this.input.set("")
                }
                ImGui.sameLine()
                if (ImGui.button("codeEditor.$MODID.no".mcTranslate.string, 120f, 0f)) {
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

    override fun shouldCloseOnEsc() = COMPLETIONS.isEmpty()
}

@Serializable
class Tree(val value: String, val path: String) {
    var drawArrow = true
    val children: MutableList<Tree> = ArrayList()

    fun insert(path: String, location: ResourceLocation, target: String) {
        val components = path.split("/", limit = 2)

        val child = children.find { it.value == components[0] }
            ?: Tree(components[0], "%$target%:$location").apply {
                this@Tree.children.add(this)
                drawArrow = false
            }

        if (components.size > 1) {
            child.drawArrow = true
            child.insert(components[1], location, target)
        }
    }

    fun sort() {
        children.sortBy { it.value }
        children.sortByDescending { it.drawArrow }
        children.forEach { it.sort() }
    }
}

val KOTLIN_LANG = TextEditorLanguageDefinition.c().apply {
    setPreprocChar('@')
    setKeywords(
        arrayOf(
            "break", "continue", "switch", "case", "try",
            "catch", "delete", "do", "while", "else", "finally", "if",
            "else", "for", "is", "as", "in", "instanceof",
            "new", "throw", "typeof", "typealias", "with", "yield", "when", "return",
            "by", "constructor", "delegate", "dynamic", "field", "get", "set", "init", "value",
            "where", "actual", "annotation", "companion", "field", "external", "infix", "inline", "inner", "internal",
            "open", "operator", "out", "override", "suspend", "vararg",
            "abstract", "extends", "final", "implements", "interface", "super", "throws",
            "data", "class", "fun", "var", "val", "import", "Java", "JSON", "void", "uniform", "using",
            "const", "uint", "float", "int", "double", "vec2", "vec3", "vec4", "sampler2D", "ifdef", "endif",
            "default", "true", "false", "package"
        )
    )

    setName("KotlinScript")

    setSingleLineComment("//")
    setCommentStart("/*")
    setCommentEnd("*/")

    setAutoIdentation(true)
}

fun ResourceManager.toServerTree(): Tree {
    val assets = Tree("data", "data")
    insertTree(assets, "", "data") {
        !it.path.startsWith("models")
    }
    for (value in listOf(
        "recipe", "advancement", "loot_table", "tags", "worldgen"
    )) {
        insertTree(assets, value, "data") { true }
    }
    return assets
}

fun ResourceManager.toClientTree(): Tree {
    val assets = Tree("assets", "assets")
    insertTree(assets, "", "assets") { true }
    for (value in listOf(
        "blockstates", "font", "lang", "shaders", "textures", "particles", "animations", "geo"
    )) {
        insertTree(assets, value, "assets") { true }
    }
    return assets
}

fun ResourceManager.insertTree(tree: Tree, path: String, target: String, filter: (ResourceLocation) -> Boolean) {
    listResources(path, filter).keys.forEach { location ->
        val child = tree.children.find { it.value == location.namespace }
            ?: Tree(location.namespace, location.namespace).apply { tree.children.add(this) }
        child.insert(location.path, location, target)
    }
}

fun Double.roundTo(numFractionDigits: Int): Double {
    val factor = 10.0.pow(numFractionDigits.toDouble())
    return (this * factor).roundToInt() / factor
}

val OPEN_IDE = KeyMapping("key.$MODID.open_ide", GLFW.GLFW_KEY_H, "key.categories.hollowengine.keys")

@SubscribeEvent
fun onKeyBinding(event: RegisterKeyBindingsEvent) {
    event.registerKeyMapping(OPEN_IDE)
}

@SubscribeEvent
fun onClientTick(event: TickEvent.Client) {
    if (OPEN_IDE.isDown) IDEGui.open()
}