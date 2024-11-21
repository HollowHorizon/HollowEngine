package ru.hollowhorizon.hollowengine.client.gui.scripting

import com.mojang.blaze3d.platform.NativeImage
import imgui.ImGuiWindowClass
import imgui.ImVec2
import imgui.flag.*
import imgui.internal.ImGui
import imgui.internal.flag.ImGuiDockNodeFlags
import imgui.type.ImBoolean
import imgui.type.ImInt
import imgui.type.ImString
import kotlinx.coroutines.runBlocking
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.DynamicTexture
import org.lwjgl.glfw.GLFW
import ru.hollowhorizon.hc.client.imgui.Graphics
import ru.hollowhorizon.hc.client.imgui.Graphics.image
import ru.hollowhorizon.hc.client.imgui.remember
import ru.hollowhorizon.hc.client.utils.literal
import ru.hollowhorizon.hc.client.utils.mc
import ru.hollowhorizon.hc.client.utils.rl
import ru.hollowhorizon.hc.common.network.request
import ru.hollowhorizon.hollowengine.HollowEngine
import ru.hollowhorizon.hollowengine.client.gui.ImGuiScreen
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.FileData
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.ImageFileData
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.TextFileData
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.completionsList
import ru.hollowhorizon.hollowengine.client.keys.Key
import java.io.ByteArrayInputStream

object IDEGuiV2 : ImGuiScreen("code_editor") {
    var currentFile = ""
    var fileToRun = ""

    val tabCount = ImInt(HollowEngine.config.ideConfig.tabSpace)
    val fontSize = ImInt(HollowEngine.config.ideConfig.fontSize)

    val files = HashSet<FileData>()
    var fileTree = TreeNode.EMPTY

    var modalAction = ModalAction.NONE
    var modalFile = ""
    val modalInput = ImString(100)

    enum class ModalAction {
        CREATE_FILE, CREATE_FOLDER, RENAME, NONE
    }

    override fun Graphics.draw() {
        ImGui.pushStyleColor(ImGuiCol.WindowBg, 0xFF302D2B.toInt())
        ImGui.pushStyleColor(ImGuiCol.PopupBg, 0xFF302D2B.toInt())
        ImGui.pushStyleColor(ImGuiCol.MenuBarBg, 0)
        ImGui.pushStyleColor(ImGuiCol.FrameBg, 0xFF221F1E.toInt())
        ImGui.pushStyleColor(ImGuiCol.Button, 0xFF221F1E.toInt())
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

        drawModals()

        ImGui.popStyleVar(2)
        ImGui.popStyleColor(12)
    }

    private fun drawMainMenu() {
        ImGui.beginMenuBar()
        val iconSize = ImGui.getFontSize().toFloat()
        val old = ImGui.getCursorPosY()
        ImGui.setCursorPos(ImGui.getStyle().windowPadding)
        image("hollowengine:textures/gui/icons/code_editor.png".rl, iconSize, iconSize)
        ImGui.sameLine()
        ImGui.setCursorPosX(ImGui.getCursorPosX() + ImGui.getStyle().itemSpacingX + 4)
        ImGui.setCursorPosY(old)

        if (ImGui.beginMenu("Файл")) {
            image("hollowengine:textures/gui/icons/reload.png".rl, iconSize, iconSize)
            ImGui.sameLine()
            if (ImGui.menuItem("Синхронизировать все файлы")) {
                updateTree()
                files.forEach { RequestFilePacket(it.filePath).send() }
            }
            image("hollowengine:textures/gui/icons/reload_mc.png".rl, iconSize, iconSize)
            ImGui.sameLine()
            if (ImGui.menuItem("Перезагрузить ресурсы")) {
                Minecraft.getInstance().reloadResourcePacks()
            }
            image("hollowengine:textures/gui/icons/close_all.png".rl, iconSize, iconSize)
            ImGui.sameLine()
            if (ImGui.menuItem("Закрыть все файлы")) files.clear()
            image("hollowengine:textures/gui/icons/exit.png".rl, iconSize, iconSize)
            ImGui.sameLine()
            if (ImGui.menuItem("Выход")) onClose()
            ImGui.endMenu()
        }
        if (ImGui.beginMenu("Правка")) {
            Graphics.textShadow("Тут пока пусто")
            ImGui.endMenu()
        }
        if (ImGui.beginMenu("Поиск")) {
            Graphics.textShadow("Тут пока пусто")
            ImGui.endMenu()
        }
        if (ImGui.beginMenu("Настройки")) {
            ImGui.pushItemWidth(110f)
            if (ImGui.inputInt("Количество пробелов за таб", tabCount)) {
                if (tabCount.get() > 9) tabCount.set(9)
                if (tabCount.get() < 1) tabCount.set(1)
                files.filterIsInstance<TextFileData>().forEach { it.textEditor.tabSize = tabCount.get() }
                HollowEngine.config.ideConfig.tabSpace = tabCount.get()
                HollowEngine.config.save()
            }
            if (ImGui.inputInt("Размер текста", fontSize)) {
                if (fontSize.get() > 100) fontSize.set(100)
                if (fontSize.get() < 8) fontSize.set(8)
                files.filterIsInstance<TextFileData>().forEach { it.fontSize = fontSize.get() }
                HollowEngine.config.ideConfig.fontSize = fontSize.get()
                HollowEngine.config.save()
            }
            ImGui.popItemWidth()
            ImGui.endMenu()
        }

        val size = 400f + (iconSize + ImGui.getStyle().itemSpacingX + ImGui.getStyle().framePaddingX * 2) * 3
        ImGui.dummy(ImGui.getContentRegionAvailX() - size, 0f)
        ImGui.pushItemWidth(400f)
        ImGui.setCursorPosY(ImGui.getCursorPosY() + 4)
        image("hollowengine:textures/gui/icons/file_kts.png".rl, iconSize, iconSize)
        ImGui.sameLine()
        ImGui.setCursorPosY(ImGui.getCursorPosY() - 4)
        val preview = if (fileToRun.isNotEmpty()) fileToRun.substringAfterLast('/') else "Пусто"
        Graphics.combo("##script_to_run", preview) {
            files.filter { it.fileName.endsWith(".kts") }.forEach { menuItem(it.fileName) { fileToRun = it.filePath } }
        }
        ImGui.popItemWidth()
        ImGui.sameLine()
        Graphics.imageButton("hollowengine:textures/gui/icons/play.png".rl, iconSize, iconSize) {
            if (fileToRun.isNotEmpty()) StartScriptPacket(fileToRun).send()
            else Minecraft.getInstance().player?.sendToast("Выбранный скрипт не существует!".literal)
        }
        Graphics.tooltipHover { textShadow("Запустить выбранный скрипт") }
        ImGui.sameLine()
        Graphics.imageButton("hollowengine:textures/gui/icons/stop.png".rl, iconSize, iconSize) {
            if (fileToRun.isNotEmpty()) StopScriptPacket(fileToRun).send()
            else Minecraft.getInstance().player?.sendToast("Выбранный скрипт не существует!".literal)
        }
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
        val fontSize = ImGui.getFontSize().toFloat()

        image("hollowengine:textures/gui/icons/file.png".rl, fontSize, fontSize)
        ImGui.sameLine()
        menuItem("Открыть") {
            RequestFilePacket(filePath).send()
            modalFile = filePath
        }

        image("hollowengine:textures/gui/icons/rename.png".rl, fontSize, fontSize)
        ImGui.sameLine()
        menuItem("Переименовать") {
            RequestFilePacket(filePath).send()
            modalAction = ModalAction.RENAME
            modalFile = filePath
        }

        image("hollowengine:textures/gui/icons/remove.png".rl, fontSize, fontSize)
        ImGui.sameLine()
        menuItem("Удалить") {
            DeleteFilePacket(filePath).send()
            updateTree()
        }
    }

    fun drawFolderPopup(popupName: String, filePath: String) = Graphics.popup(popupName) {
        val fontSize = ImGui.getFontSize().toFloat()

        image("hollowengine:textures/gui/icons/add.png".rl, fontSize, fontSize)
        ImGui.sameLine()
        if (ImGui.beginMenu("Создать")) {
            image("hollowengine:textures/gui/icons/create_folder.png".rl, fontSize, fontSize)
            ImGui.sameLine()
            menuItem("Папка") {
                modalAction = ModalAction.CREATE_FOLDER
                modalFile = filePath
            }

            image("hollowengine:textures/gui/icons/create_file.png".rl, fontSize, fontSize)
            ImGui.sameLine()
            menuItem("Файл") {
                modalAction = ModalAction.CREATE_FILE
                modalFile = filePath
            }
            ImGui.endMenu()
        }

        image("hollowengine:textures/gui/icons/rename.png".rl, fontSize, fontSize)
        ImGui.sameLine()
        menuItem("Переименовать") {
            RequestFilePacket(filePath).send()
            modalAction = ModalAction.RENAME
            modalFile = filePath
        }

        image("hollowengine:textures/gui/icons/remove.png".rl, fontSize, fontSize)
        ImGui.sameLine()
        menuItem("Удалить") {
            DeleteFilePacket(filePath).send()
            updateTree()
        }
    }

    private fun drawModals() {
        val modalName = when (modalAction) {
            ModalAction.CREATE_FILE -> "Создание файла"
            ModalAction.CREATE_FOLDER -> "Создание папки"
            ModalAction.RENAME -> "Переименование"
            else -> ""
        }

        if (modalAction != ModalAction.NONE) ImGui.openPopup(modalName)

        ImGui.pushStyleColor(ImGuiCol.TitleBgActive, 0xFF221F1E.toInt())

        if (ImGui.beginPopupModal(modalName)) {
            when (modalAction) {
                ModalAction.CREATE_FILE, ModalAction.CREATE_FOLDER -> {
                    val target = if (modalAction == ModalAction.CREATE_FILE) "файла" else "папки"
                    Graphics.text("Введите имя $target для создания:")
                    val textSize = ImGui.calcTextSizeX("Введите имя $target для создания:")
                    ImGui.pushItemWidth(textSize + ImGui.getStyle().itemSpacingX)
                    ImGui.inputText("##new_file", modalInput)
                    ImGui.popItemWidth()
                    if ((ImGui.button(
                            "Подтвердить",
                            textSize / 2f,
                            ImGui.getFontSize().toFloat() + 8f
                        ) || Key.ENTER.isPressed()) && modalFile.isNotEmpty()
                    ) {
                        CreateFilePacket(modalFile + "/" + modalInput.get()).send()
                        modalInput.clear()
                        modalAction = ModalAction.NONE
                        updateTree()
                    }
                    ImGui.sameLine()
                    if (ImGui.button("Отмена", textSize / 2f, ImGui.getFontSize().toFloat() + 8f)) {
                        modalAction = ModalAction.NONE
                    }
                }

                ModalAction.RENAME -> {
                    Graphics.text("Введите имя файла для переименования:")
                    ImGui.inputText("##rename_file", modalInput)
                    if ((ImGui.button("Подтвердить") || Key.ENTER.isPressed()) && modalFile.isNotEmpty()) {
                        RenameFilePacket(modalFile, modalInput.get()).send()
                        modalInput.clear()
                        modalAction = ModalAction.NONE
                        updateTree()
                    }
                    ImGui.sameLine()
                    if (ImGui.button("Отмена")) {
                        modalAction = ModalAction.NONE
                    }
                }

                else -> {
                    modalAction = ModalAction.NONE
                }
            }
            ImGui.endPopup()
        }

        ImGui.popStyleColor()
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
                val isFileFocused = ImGui.isWindowFocused(ImGuiFocusedFlags.ChildWindows)
                file.draw()
                if (isFileFocused && Key.LEFT_CONTROL.isPressed() && GLFW.glfwGetKey(
                        mc.window.window,
                        GLFW.GLFW_KEY_W
                    ) == GLFW.GLFW_PRESS
                ) {
                    file.isOpen.set(false)
                }
            }
            ImGui.end()

            val remove = wasOpened && !file.isOpen.get()
            if(remove) file.destroy()
            remove
        }
        if (files.isEmpty()) fileToRun = ""
        ImGui.end()
    }

    fun openFile(path: String, bytes: ByteArray, type: FileType) {
        files.removeIf {
            val remove = it.filePath == path
            if(remove) it.destroy()
            remove
        }
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
        if (fileToRun == "" && path.endsWith(".kts")) fileToRun = path
    }

    override fun onClose() {
        super.onClose()
        files.clear()
    }

    private fun updateTree() = runBlocking {
        fileTree = RequestTreePacket().request().tree
    }

    override fun shouldCloseOnEsc() = completionsList.isEmpty()
}