package ru.hollowhorizon.hollowengine.client.gui.scripting

import androidx.compose.runtime.Composable
import ru.hollowhorizon.hollowengine.client.ui.*
import ru.hollowhorizon.hollowengine.generated.Assets.Hollowengine.Textures.Gui.Icons.COPY
import ru.hollowhorizon.hollowengine.generated.Assets.Hollowengine.Textures.Gui.Icons.CREATE_FILE
import ru.hollowhorizon.hollowengine.generated.Assets.Hollowengine.Textures.Gui.Icons.CREATE_FOLDER
import ru.hollowhorizon.hollowengine.generated.Assets.Hollowengine.Textures.Gui.Icons.CUT
import ru.hollowhorizon.hollowengine.generated.Assets.Hollowengine.Textures.Gui.Icons.FOLDER
import ru.hollowhorizon.hollowengine.generated.Assets.Hollowengine.Textures.Gui.Icons.PASTE
import ru.hollowhorizon.hollowengine.generated.Assets.Hollowengine.Textures.Gui.Icons.REMOVE
import ru.hollowhorizon.hollowengine.generated.Assets.Hollowengine.Textures.Gui.Icons.RENAME

@Composable
internal fun HollowIdeProjectContextMenu(
    menu: ProjectContextMenu?,
    onCreateFile: (String) -> Unit,
    onCreateFolder: (String) -> Unit,
    onRename: (String) -> Unit,
    onCopy: (String) -> Unit,
    onCut: (String) -> Unit,
    onPaste: (String) -> Unit,
    onShowInExplorer: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    if (menu == null) return
    Popup(
        anchor = UiPopupAnchor.Cursor(menu.x, menu.y),
        alignment = UiPopupAlignment.Cursor,
        id = "project-context-menu",
        tags = listOf("dropdown-popup", "project-context-menu"),
    ) {
        ProjectMenuItem("New File", "Ctrl+N", CREATE_FILE.toString()) { onCreateFile(menu.path) }
        ProjectMenuItem("New Folder", "Ctrl+Shift+N", CREATE_FOLDER.toString()) { onCreateFolder(menu.path) }
        ProjectMenuItem("Rename", "F2", RENAME.toString()) { onRename(menu.path) }
        ProjectMenuItem("Copy", "Ctrl+C", COPY.toString()) { onCopy(menu.path) }
        ProjectMenuItem("Cut", "Ctrl+X", CUT.toString()) { onCut(menu.path) }
        ProjectMenuItem("Paste", "Ctrl+V", PASTE.toString()) { onPaste(menu.path) }
        ProjectMenuItem("Show in Explorer", "", FOLDER.toString()) { onShowInExplorer(menu.path) }
        ProjectMenuItem("Delete", "Del", REMOVE.toString()) { onDelete(menu.path) }
    }
}

@Composable
internal fun HollowIdeProjectNameDialog(
    dialog: ProjectNameDialog?,
    onNameChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    if (dialog == null) return
    Popup(
        anchor = UiPopupAnchor.Cursor(dialog.x, dialog.y),
        alignment = UiPopupAlignment.Cursor,
        id = "project-name-dialog",
        tags = listOf("dropdown-popup", "project-name-dialog"),
    ) {
        Text(dialog.title, tags = listOf("project-name-dialog-title"))
        TextField(
            value = dialog.name,
            onChange = onNameChange,
            id = "project-name-dialog-input",
            tags = listOf("project-name-dialog-input"),
            modifier = Modifier.size(180.px, 24.px),
        )
        Row(tags = listOf("project-name-dialog-actions")) {
            ProjectMenuItem("OK", "", icon = null, action = onConfirm)
            ProjectMenuItem("Cancel", "", icon = null, action = onCancel)
        }
    }
}

@Composable
private fun ProjectMenuItem(label: String, shortcut: String, icon: String? = null, action: () -> Unit) {
    Row(
        tags = listOf("dropdown-item", "project-context-menu-item"),
        modifier = Modifier.input(hoverable = true, clickable = true)
            .cursor(UiCursorShape.HAND)
            .alignItems(vertical = UiAlign.CENTER)
            .onClick { event ->
                action()
                event.consume()
            }
    ) {
        if (icon != null) Image(icon, tags = listOf("dropdown-item-icon", "project-context-menu-icon"))
        Text(label, tags = listOf("dropdown-item-label", "project-context-menu-label"))
        if (shortcut.isNotBlank()) Text(shortcut, tags = listOf("dropdown-item-label", "project-context-menu-shortcut"))
    }
}

internal data class ProjectContextMenu(
    val path: String,
    val x: Float,
    val y: Float,
)

internal data class ProjectNameDialog(
    val action: ProjectNameAction,
    val path: String,
    val name: String,
    val x: Float,
    val y: Float,
) {
    val title: String
        get() = when (action) {
            ProjectNameAction.CreateFile -> "New File"
            ProjectNameAction.CreateFolder -> "New Folder"
            ProjectNameAction.Rename -> "Rename"
        }
}

internal enum class ProjectNameAction {
    CreateFile,
    CreateFolder,
    Rename,
}

internal fun HollowIdeFileOperationResult.statusText(): String {
    return when (this) {
        HollowIdeFileOperationResult.Success -> ""
        HollowIdeFileOperationResult.InvalidName -> "Invalid file name"
        HollowIdeFileOperationResult.AlreadyExists -> "File already exists"
        HollowIdeFileOperationResult.NotFound -> "File not found"
    }
}
