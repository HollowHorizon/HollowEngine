package ru.hollowhorizon.hollowengine.client.gui.scripting

import com.mojang.blaze3d.platform.NativeImage
import imgui.ImGuiWindowClass
import imgui.ImVec2
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiCond
import imgui.flag.ImGuiDir
import imgui.flag.ImGuiStyleVar
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
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.FileData
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.ImageFileData
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.TextFileData
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.completionsList
import ru.hollowhorizon.hollowengine.common.scripting.core.ScriptError
import java.io.ByteArrayInputStream

object IDEGuiV2 : ImGuiScreen("code_editor") {
    var currentFile = ""
    var fileToRun = ""

    val files = HashSet<FileData>()
    var fileTree = TreeNode.EMPTY

    override fun Graphics.draw() {
        ImGui.pushStyleColor(ImGuiCol.WindowBg, 0xFF302D2B.toInt())
        ImGui.pushStyleColor(ImGuiCol.PopupBg, 0xFF302D2B.toInt())
        ImGui.pushStyleColor(ImGuiCol.MenuBarBg, 0)
        ImGui.pushStyleColor(ImGuiCol.FrameBg, 0xFF302D2B.toInt())
        ImGui.pushStyleColor(ImGuiCol.Button, 0xFF302D2B.toInt())
        ImGui.pushStyleColor(ImGuiCol.TitleBgActive, 0xFF302D2B.toInt())
        ImGui.pushStyleColor(ImGuiCol.NavHighlight, 0xFF4A4543.toInt())
        ImGui.pushStyleColor(ImGuiCol.Border, 0xFF4A4543.toInt())

        ImGui.pushStyleColor(ImGuiCol.ScrollbarBg, 0)
        ImGui.pushStyleColor(ImGuiCol.ScrollbarGrab, 0xFF4A4543.toInt())
        ImGui.pushStyleColor(ImGuiCol.ScrollbarGrabHovered, 0xFF544E4C.toInt())
        ImGui.pushStyleColor(ImGuiCol.ScrollbarGrabActive, 0xFF544E4C.toInt())

        ImGui.pushStyleVar(ImGuiStyleVar.PopupRounding, 15f)
        ImGui.pushStyleVar(ImGuiStyleVar.PopupBorderSize, 3f)


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
        ImGui.setCursorPos(old + ImVec2(size + ImGui.getStyle().itemSpacingX + 4, 0f))
        ImGui.getForegroundDrawList().apply {
            addLine(
                ImGui.getCursorScreenPos(),
                ImGui.getCursorScreenPos() + ImVec2(0f, ImGui.getContentRegionMaxY()),
                ImGui.getStyle().getColor(ImGuiCol.Separator).color
            )
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
            pLeftNode.localFlags =
                pLeftNode.localFlags or ImGuiDockNodeFlags.NoTabBar or ImGuiDockNodeFlags.NoDockingOverMe or ImGuiDockNodeFlags.NoDockingSplitMe
            val pRightNode = ImGui.dockBuilderGetNode(rightDockID.get())
            pRightNode.localFlags =
                pRightNode.localFlags or ImGuiDockNodeFlags.NoTabBar or ImGuiDockNodeFlags.NoWindowMenuButton

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
            workspaceWindowClass
        )

        ImGui.end()

        if (showProject) drawFileTree(projectName)

        ImGui.pushStyleColor(ImGuiCol.DockingEmptyBg, 0)
        ImGui.pushStyleColor(ImGuiCol.WindowBg, 0xFF221F1E.toInt())
        ImGui.pushStyleColor(ImGuiCol.TabActive, 0xFF4A4543.toInt())
        ImGui.pushStyleColor(ImGuiCol.TabHovered, 0xFF4A4543.toInt())
        ImGui.pushStyleColor(ImGuiCol.Tab, 0xFF302D2B.toInt())
        ImGui.pushStyleColor(ImGuiCol.TabUnfocused, 0xFF302D2B.toInt())
        ImGui.pushStyleColor(ImGuiCol.TabUnfocusedActive, 0xFF302D2B.toInt())

        ImGui.pushStyleVar(ImGuiStyleVar.TabRounding, 0f)
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 0f, 0f)
        drawActiveFiles(contextName, rightDockID)
        ImGui.popStyleVar(2)
        ImGui.popStyleColor(7)

        ImGui.popStyleVar(2)
        ImGui.popStyleColor(12)
    }

    private fun drawMainMenu() {
        ImGui.beginMenuBar()
        val iconSize = ImGui.getFontSize().toFloat()
        val old = ImGui.getCursorPosY()
        ImGui.setCursorPos(ImGui.getStyle().windowPadding)
        Graphics.image("hollowengine:textures/gui/icons/code_editor.png".rl, iconSize, iconSize)
        ImGui.sameLine()
        ImGui.setCursorPosX(ImGui.getCursorPosX() + ImGui.getStyle().itemSpacingX + 4)
        ImGui.setCursorPosY(old)

        fun nothing() = Graphics.textShadow("Тут пока ничего нет...")

        if (ImGui.beginMenu("Файл")) {
            if(ImGui.menuItem("Импорт скрипта")) {}
            if(ImGui.menuItem("Обновить файлы")) {}
            if(ImGui.menuItem("Выход")) {}
            ImGui.endMenu()
        }
        if (ImGui.beginMenu("Правка")) ImGui.endMenu()
        if (ImGui.beginMenu("Поиск")) ImGui.endMenu()
        if (ImGui.beginMenu("Настройки")) ImGui.endMenu()

        val size = 400f + (iconSize + ImGui.getStyle().itemSpacingX + ImGui.getStyle().framePaddingX * 2) * 3
        ImGui.dummy(ImGui.getContentRegionAvailX() - size, 0f)
        ImGui.pushItemWidth(400f)
        ImGui.setCursorPosY(ImGui.getCursorPosY() + 4)
        Graphics.image("hollowengine:textures/gui/icons/file_kts.png".rl, iconSize, iconSize)
        ImGui.sameLine()
        ImGui.setCursorPosY(ImGui.getCursorPosY() - 4)
        val preview = if(fileToRun.isNotEmpty()) fileToRun.substringAfterLast('/') else "Пусто"
        Graphics.combo("##script_to_run", preview) {
            files.forEach { menuItem(it.fileName) { fileToRun = it.filePath } }
        }
        ImGui.popItemWidth()
        ImGui.sameLine()
        Graphics.imageButton("hollowengine:textures/gui/icons/play.png".rl, iconSize, iconSize) {}
        Graphics.tooltipHover { textShadow("Запустить выбранный скрипт") }
        ImGui.sameLine()
        Graphics.imageButton("hollowengine:textures/gui/icons/stop.png".rl, iconSize, iconSize) {}
        Graphics.tooltipHover { textShadow("Остановить выбранный скрипт") }


        ImGui.endMenuBar()
        ImGui.separator()
    }

    private fun drawFileTree(windowName: String) {
        if (ImGui.begin(
                windowName,
                ImGuiWindowFlags.NoCollapse or ImGuiWindowFlags.NoResize
            )
        ) {
            fileTree.draw(::drawFolderPopup, ::drawFilePopup)
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
        ImGui.dockSpace(dockId, 0f, 0f, ImGuiDockNodeFlags.NoCloseButton or ImGuiDockNodeFlags.NoWindowMenuButton or ImGuiDockNodeFlags.NoDockingOverMe)
        files.removeIf { file ->
            ImGui.setNextWindowDockID(dockId, ImGuiCond.FirstUseEver)
            val wasOpened = file.isOpen.get()
            if (ImGui.begin(file.fileName, file.isOpen, ImGuiWindowFlags.NoCollapse)) {
                file.draw()
            }
            ImGui.end()

            wasOpened && !file.isOpen.get()
        }
        if(files.isEmpty()) fileToRun = ""
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
        currentFile = path
        if(fileToRun == "") fileToRun = path
    }

    override fun onClose() {
        super.onClose()
        files.clear()
    }

    override fun shouldCloseOnEsc() = completionsList.isEmpty()
}