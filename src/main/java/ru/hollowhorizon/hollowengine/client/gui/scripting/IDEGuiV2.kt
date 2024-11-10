package ru.hollowhorizon.hollowengine.client.gui.scripting

import com.mojang.blaze3d.platform.NativeImage
import imgui.ImGuiWindowClass
import imgui.ImVec2
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiCond
import imgui.flag.ImGuiDir
import imgui.flag.ImGuiWindowFlags
import imgui.internal.ImGui
import imgui.internal.flag.ImGuiDockNodeFlags
import imgui.type.ImBoolean
import imgui.type.ImInt
import net.minecraft.client.renderer.texture.DynamicTexture
import ru.hollowhorizon.hc.client.imgui.Graphics
import ru.hollowhorizon.hc.client.imgui.remember
import ru.hollowhorizon.hc.client.utils.mc
import ru.hollowhorizon.hc.client.utils.rl
import ru.hollowhorizon.hollowengine.client.gui.ImGuiScreen
import ru.hollowhorizon.hollowengine.client.gui.addLine
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.FileData
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.ImageFileData
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.TextFileData
import ru.hollowhorizon.hollowengine.common.scripting.core.ScriptError
import java.io.ByteArrayInputStream

object IDEGuiV2 : ImGuiScreen() {
    var fileErrors: List<ScriptError> = emptyList()
    var currentFile = 0

    val files = HashSet<FileData>()
    var fileTree = TreeNode.EMPTY

    override fun Graphics.draw() {
        val window = mc.window
        ImGui.setNextWindowPos(0f, 0f)
        ImGui.setNextWindowSize(window.width.toFloat(), window.height.toFloat())
        val drawContent = ImGui.begin(
            "Редактор кода",
            ImGuiWindowFlags.MenuBar or ImGuiWindowFlags.NoMove or ImGuiWindowFlags.NoResize or ImGuiWindowFlags.NoCollapse or ImGuiWindowFlags.NoTitleBar
        )
        drawMainMenu()

        val old = ImGui.getCursorPos()
        var showProject by remember { true }
        val size = ImGui.getFontSize().toFloat()
        imageButton("hollowengine:textures/gui/icons/folder${if (showProject) "_open" else ""}.png".rl, size, size) {
            showProject = !showProject
        }
        tooltipHover { textShadow("Проект") }
        imageButton("hollowengine:textures/gui/icons/run.png".rl, size, size) {
        }
        tooltipHover { textShadow("Консоль (Пока не работает)") }
        ImGui.sameLine()
        ImGui.setCursorPos(old + ImVec2(size + ImGui.getStyle().itemSpacingX +4, 0f))
        ImGui.getForegroundDrawList().apply {
            addLine(ImGui.getCursorScreenPos(), ImGui.getCursorScreenPos() + ImVec2(0f, ImGui.getContentRegionMaxY()), ImGui.getStyle().getColor(ImGuiCol.Separator).color)
            addLine(ImGui.getCursorScreenPos(), ImGui.getCursorScreenPos() + ImVec2(ImGui.getContentRegionMaxX(), 0f), ImGui.getStyle().getColor(ImGuiCol.Separator).color)
        }

        val dockspaceId = ImGui.getID("MainDockspace")
        val workspaceWindowClass = ImGuiWindowClass()
        workspaceWindowClass.classId = dockspaceId
        workspaceWindowClass.dockingAllowUnclassed = false

        val projectName = "Проект"
        val contextName = "ContextWindow"

        val rightDockID = ImInt(0)

        if (ImGui.dockBuilderGetNode(dockspaceId).ptr == 0L) {
            ImGui.dockBuilderAddNode(
                dockspaceId,
                ImGuiDockNodeFlags.DockSpace or ImGuiDockNodeFlags.NoWindowMenuButton or ImGuiDockNodeFlags.NoCloseButton or ImGuiDockNodeFlags.NoDockingSplitMe or
                ImGuiDockNodeFlags.NoDockingOverMe or ImGuiDockNodeFlags.NoDocking or ImGuiDockNodeFlags.NoDockingOverOther
            )
            val region = ImGui.getContentRegionAvail()
            ImGui.dockBuilderSetNodeSize(dockspaceId, region.x, region.y)

            val leftDockID = ImInt(0)
            ImGui.dockBuilderSplitNode(dockspaceId, ImGuiDir.Left, 0.4f, leftDockID, rightDockID)

            val pLeftNode = ImGui.dockBuilderGetNode(leftDockID.get())
            pLeftNode.localFlags = pLeftNode.localFlags or ImGuiDockNodeFlags.NoTabBar or ImGuiDockNodeFlags.NoDockingOverMe or ImGuiDockNodeFlags.NoDockingSplitMe
            val pRightNode = ImGui.dockBuilderGetNode(rightDockID.get())
            pRightNode.localFlags = pRightNode.localFlags or ImGuiDockNodeFlags.NoTabBar or ImGuiDockNodeFlags.NoWindowMenuButton

            ImGui.dockBuilderDockWindow(projectName, leftDockID.get())
            ImGui.dockBuilderDockWindow(contextName, rightDockID.get())

            ImGui.dockBuilderFinish(dockspaceId)
        }

        val dockFlags = if (drawContent) imgui.flag.ImGuiDockNodeFlags.None
        else imgui.flag.ImGuiDockNodeFlags.KeepAliveOnly
        val region = imgui.ImGui.getContentRegionAvail()
        ImGui.dockSpace(
            dockspaceId, region.x, region.y,
            dockFlags or ImGuiDockNodeFlags.NoDocking or ImGuiDockNodeFlags.NoTabBar or
            ImGuiDockNodeFlags.NoDockingSplitMe or ImGuiDockNodeFlags.NoDockingOverMe,
            workspaceWindowClass)

        ImGui.end()

        if (showProject) drawFileTree(projectName)

        ImGui.pushStyleColor(ImGuiCol.DockingEmptyBg, 0)
        drawActiveFiles(contextName, rightDockID)
        ImGui.popStyleColor()
    }

    private fun drawMainMenu() {
        ImGui.beginMenuBar()
        val iconSize = ImGui.getFontSize().toFloat()
        val old = ImGui.getCursorPosY()
        ImGui.setCursorPosY(ImGui.getStyle().windowPaddingY)
        Graphics.image("hollowengine:textures/gui/icons/code_editor.png".rl, iconSize, iconSize)
        ImGui.sameLine()
        ImGui.setCursorPosY(old)

        fun nothing() = Graphics.textShadow("Тут пока ничего нет...")

        if (ImGui.menuItem("Файл")) ImGui.openPopup("Menu")
        if (ImGui.menuItem("Правка")) ImGui.openPopup("Menu")
        if (ImGui.menuItem("Поиск")) ImGui.openPopup("Menu")
        if (ImGui.menuItem("Настройки")) ImGui.openPopup("Menu")

        Graphics.popup("Menu") {
            nothing()
        }

        val size = 400f + (iconSize + ImGui.getStyle().itemSpacingX + ImGui.getStyle().framePaddingX*2) * 3
        ImGui.dummy(ImGui.getContentRegionAvailX() - size, 0f)
        ImGui.pushItemWidth(400f)
        ImGui.setCursorPosY(ImGui.getCursorPosY() + 4)
        Graphics.image("hollowengine:textures/gui/icons/file_kts.png".rl, iconSize, iconSize)
        ImGui.sameLine()
        ImGui.setCursorPosY(ImGui.getCursorPosY() - 4)
        Graphics.combo("##script_to_run", "example.story.kts") {
            menuItem("example.story.kts") {}
            menuItem("npc.story.kts") {}
        }
        ImGui.popItemWidth()
        ImGui.sameLine()
        Graphics.imageButton("hollowengine:textures/gui/icons/play.png".rl, iconSize, iconSize){}
        ImGui.sameLine()
        Graphics.imageButton("hollowengine:textures/gui/icons/stop.png".rl, iconSize, iconSize){}


        ImGui.endMenuBar()
    }

    private fun drawFileTree(windowName: String) {
        if (ImGui.begin(
                windowName,
                ImGuiWindowFlags.NoCollapse or ImGuiWindowFlags.NoResize
            )
        ) {
            fileTree.draw(::drawFilePopup, ::drawFolderPopup)
        }
        ImGui.end()
    }

    fun drawFilePopup(popupName: String, filePath: String) = Graphics.popup(popupName) {
        val items = listOf(
            "Открыть" to { RequestFilePacket(filePath).send() },
            "Переименовать" to { },
            "Удалить" to { DeleteFilePacket(filePath).send() },
        )

        items.forEach { menuItem(it.first) { it.second() } }
    }

    fun drawFolderPopup(popupName: String, filePath: String) = Graphics.popup(popupName) {
        val items = listOf(
            "Создать папку" to { },
            "Создать файл" to { },
            "Удалить папку" to { DeleteFilePacket(filePath).send() },
        )

        items.forEach { menuItem(it.first) { it.second() } }
    }

    private fun drawActiveFiles(windowName: String, rightDockID: ImInt) {
        ImGui.begin(
            windowName,
            ImGuiWindowFlags.NoCollapse or ImGuiWindowFlags.NoTitleBar
        )
        val dockId = ImGui.getID("FilesContext")
        ImGui.dockSpace(dockId, 0f, 0f, ImGuiDockNodeFlags.NoCloseButton or ImGuiDockNodeFlags.NoWindowMenuButton)
        files.removeIf { file ->
            ImGui.setNextWindowDockID(dockId, ImGuiCond.FirstUseEver)
            val wasOpened = file.isOpen.get()
            if (ImGui.begin(file.fileName, file.isOpen, ImGuiWindowFlags.NoCollapse)) {
                file.draw()
            }
            ImGui.end()

            wasOpened && !file.isOpen.get()
        }
        ImGui.end()
    }

    fun openFile(path: String, bytes: ByteArray, type: FileType) {
        files.removeIf { it.filePath == path }
        files.add(
            when (type) {
                FileType.IMAGE -> {
                    val image = NativeImage.read(ByteArrayInputStream(bytes))
                    val texture = DynamicTexture(image)

                    ImageFileData(this, path.substringAfterLast('/'), path, ImBoolean(true), texture)
                }

                FileType.TEXT -> TextFileData(this, path.substringAfterLast('/'), path, ImBoolean(true), String(bytes))
            }
        )
        currentFile = files.size - 1
    }
}