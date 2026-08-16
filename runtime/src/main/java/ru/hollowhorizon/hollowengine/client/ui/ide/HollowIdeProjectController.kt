package ru.hollowhorizon.hollowengine.client.ui.ide

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.lwjgl.glfw.GLFW
import ru.hollowhorizon.hollowengine.client.ui.UiEvent
import ru.hollowhorizon.hollowengine.client.ui.widgets.UiTreeItem
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager.fromReadablePath
import ru.hollowhorizon.hollowengine.common.utils.DesktopUtil

internal class HollowIdeProjectController(
    private val model: HollowIdeModel,
    private val focusProjectTree: () -> Unit,
    private val shortcutsActive: () -> Boolean,
    private val closeDockItem: (String) -> Unit,
    private val openFile: (HollowIdeOpenFile) -> Unit,
    private val setStatus: (String) -> Unit,
    private val pointerX: () -> Float,
    private val pointerY: () -> Float,
) {
    var contextMenu by mutableStateOf<ProjectContextMenu?>(null)
        private set
    var nameDialog by mutableStateOf<ProjectNameDialog?>(null)
        private set
    private var lastClickPath: String = ""
    private var lastClickAtMillis: Long = 0L

    fun toggle(item: UiTreeItem<HollowIdeFileNode>) {
        focusProjectTree()
        contextMenu = null
        model.toggle(item.payload)
    }

    fun select(item: UiTreeItem<HollowIdeFileNode>, event: UiEvent) {
        focusProjectTree()
        val additive = event.modifiers and GLFW.GLFW_MOD_CONTROL != 0
        if (event.button == 1) {
            model.focusSelection(item.payload)
            contextMenu = ProjectContextMenu(
                path = item.payload.path,
                x = event.x,
                y = event.y,
                canCreateSoundEvents = canCreateSoundEvents(item.payload),
            )
            nameDialog = null
            return
        }
        contextMenu = null
        if (additive) {
            model.select(item.payload, additive = true)
            return
        }
        val doubleClick = isDoubleClick(item.payload.path)
        model.select(item.payload)
        if (!doubleClick) return
        rememberClick("")
        when (val result = model.open(item.payload)) {
            HollowIdeOpenResult.Directory -> Unit
            HollowIdeOpenResult.Unsupported -> setStatus("Unsupported or binary file: ${item.payload.path}")
            is HollowIdeOpenResult.File -> openFile(result.file)
        }
    }

    fun createSoundEvents(path: String) {
        contextMenu = null
        when (val result = model.createSoundsFile(path)) {
            is HollowIdeOpenResult.File -> openFile(result.file)
            else -> setStatus("Не удалось создать sounds.json")
        }
    }

    fun openCreateFileDialog(path: String) = openNameDialog(ProjectNameAction.CreateFile, path)

    fun openCreateFolderDialog(path: String) = openNameDialog(ProjectNameAction.CreateFolder, path)

    fun openRenameDialog(path: String) = openNameDialog(ProjectNameAction.Rename, path)

    fun updateNameDialog(name: String) {
        nameDialog = nameDialog?.copy(name = name)
    }

    fun applyNameDialog() {
        val dialog = nameDialog ?: return
        val result = when (dialog.action) {
            ProjectNameAction.CreateFile -> model.createFile(dialog.path, dialog.name)
            ProjectNameAction.CreateFolder -> model.createFolder(dialog.path, dialog.name)
            ProjectNameAction.Rename -> rename(dialog)
        }
        setStatus(result.statusText())
        if (result == HollowIdeFileOperationResult.Success) nameDialog = null
    }

    fun cancelNameDialog() {
        nameDialog = null
    }

    fun copy(path: String = model.selectedTreePath, cut: Boolean = false) {
        val paths = model.selectedOr(path)
        setStatus(model.copyToClipboard(paths, cut).statusText())
        contextMenu = null
    }

    fun pasteInto(path: String = model.selectedTreePath) {
        setStatus(model.pasteInto(path).statusText())
        contextMenu = null
    }

    /** Drag and drop inside the tree: moves [path] into the folder [targetPath] belongs to. */
    fun moveInto(path: String, targetPath: String): Boolean {
        if (path == targetPath) return false
        val result = model.moveInto(listOf(path), targetPath)
        setStatus(result.statusText())
        return result == HollowIdeFileOperationResult.Success
    }

    fun showInExplorer(path: String) {
        DesktopUtil.openInExplorer(path.fromReadablePath())
        contextMenu = null
    }

    fun delete(path: String = model.selectedTreePath) {
        setStatus(model.delete(model.selectedOr(path)).statusText())
        contextMenu = null
    }

    fun handleNameDialogKey(key: Int): Boolean {
        if (nameDialog == null) return false
        return when (key) {
            GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                applyNameDialog()
                true
            }
            GLFW.GLFW_KEY_ESCAPE -> {
                cancelNameDialog()
                true
            }
            else -> false
        }
    }

    fun handleShortcut(key: Int, modifiers: Int): Boolean {
        val command = modifiers and GLFW.GLFW_MOD_CONTROL != 0
        if (!shortcutsActive()) return false
        return when {
            command && key == GLFW.GLFW_KEY_C -> {
                copy(cut = false)
                true
            }
            command && key == GLFW.GLFW_KEY_X -> {
                copy(cut = true)
                true
            }
            command && key == GLFW.GLFW_KEY_V -> {
                pasteInto()
                true
            }
            key == GLFW.GLFW_KEY_DELETE -> {
                delete()
                true
            }
            key == GLFW.GLFW_KEY_F2 && model.selectedTreePath.isNotBlank() -> {
                openRenameDialog(model.selectedTreePath)
                true
            }
            key == GLFW.GLFW_KEY_INSERT && modifiers and GLFW.GLFW_MOD_ALT != 0 &&
                    modifiers and GLFW.GLFW_MOD_SHIFT != 0 -> {
                openCreateFolderDialog(model.selectedTreePath)
                true
            }
            key == GLFW.GLFW_KEY_INSERT && modifiers and GLFW.GLFW_MOD_ALT != 0 -> {
                openCreateFileDialog(model.selectedTreePath)
                true
            }
            else -> false
        }
    }

    fun hasOpenPopup(): Boolean {
        return contextMenu != null || nameDialog != null
    }

    fun closePopups(): Boolean {
        val changed = hasOpenPopup()
        contextMenu = null
        nameDialog = null
        return changed
    }

    private fun rename(dialog: ProjectNameDialog): HollowIdeFileOperationResult {
        val oldPath = dialog.path
        val parent = oldPath.substringBeforeLast('/', "")
        val newPath = if (parent.isBlank()) dialog.name.trim() else "$parent/${dialog.name.trim()}"
        val result = model.rename(oldPath, dialog.name)
        if (result == HollowIdeFileOperationResult.Success) {
            closeDockItem(fileDockItemId(oldPath))
            model.files[newPath]?.let(openFile)
        }
        return result
    }

    private fun openNameDialog(action: ProjectNameAction, path: String) {
        val defaultName = when (action) {
            ProjectNameAction.Rename -> path.substringAfterLast('/')
            ProjectNameAction.CreateFile -> "new_file.kts"
            ProjectNameAction.CreateFolder -> "new_folder"
        }
        val menu = contextMenu
        contextMenu = null
        nameDialog = ProjectNameDialog(
            action = action,
            path = path,
            name = defaultName,
            x = menu?.x ?: pointerX(),
            y = menu?.y ?: pointerY(),
        )
    }

    private fun canCreateSoundEvents(node: HollowIdeFileNode): Boolean {
        if (!node.isDirectory || !NamespaceFolderRegex.matches(node.path)) return false
        return !"${node.path}/sounds.json".fromReadablePath().exists()
    }

    private fun isDoubleClick(path: String): Boolean {
        val now = System.currentTimeMillis()
        val doubleClick = lastClickPath == path && now - lastClickAtMillis <= ProjectTreeDoubleClickMillis
        rememberClick(path, now)
        return doubleClick
    }

    private fun rememberClick(path: String, now: Long = System.currentTimeMillis()) {
        lastClickPath = path
        lastClickAtMillis = now
    }
}

private const val ProjectTreeDoubleClickMillis = 350L

/** Resource-namespace folders like `assets/<modid>` where a `sounds.json` belongs. */
private val NamespaceFolderRegex = Regex("^assets/[^/]+$")
