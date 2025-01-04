package ru.hollowhorizon.hollowengine.client.gui.scripting

import de.fabmax.kool.math.Vec2f
import de.fabmax.kool.modules.ui2.*
import ru.hollowhorizon.hc.client.utils.rl
import ru.hollowhorizon.hc.common.coroutines.scopeSync
import ru.hollowhorizon.hc.common.network.request
import ru.hollowhorizon.hollowengine.client.gui.kool.backgroundMid
import ru.hollowhorizon.hollowengine.client.gui.scripting.popup.ItemPopupMenu
import ru.hollowhorizon.hollowengine.client.gui.scripting.popup.SubMenuItem

fun makeMenu(
    item: TreeNode,
    editFilePopup: ItemPopupMenu<TreeNode>,
    editFolderPopup: ItemPopupMenu<TreeNode>,
    renameFilePopup: ItemPopupMenu<TreeNode>,
    deleteFilePopup: ItemPopupMenu<TreeNode>,
) = SubMenuItem<TreeNode?> {
    if (item.isFolder) {
        subMenu("Создать", "hollowengine:textures/gui/icons/add.png") {
            item("Папка", "hollowengine:textures/gui/icons/create_folder.png") {
                editFolderPopup.show(Vec2f(0f, 0f), SubMenuItem { }, item)
            }
            item("Скрипт", "hollowengine:textures/gui/icons/create_file.png") {
                editFilePopup.show(Vec2f(0f, 0f), SubMenuItem { }, item)
            }
        }
        divider()
    } else {
        item("Открыть", "hollowengine:textures/gui/icons/file_kts.png") {
            RequestFilePacket(item.treePath).send()
        }
        divider()
    }

    item("Переименовать", "hollowengine:textures/gui/icons/rename.png") {
        renameFilePopup.show(Vec2f(0f, 0f), SubMenuItem { }, item)
    }
    item("Удалить", "hollowengine:textures/gui/icons/remove.png") {
        deleteFilePopup.show(Vec2f(0f, 0f), SubMenuItem { }, item)
    }
}

fun EditPopup(label: String, renamePopup: Boolean): ItemPopupMenu<TreeNode> {
    val popup = ItemPopupMenu<TreeNode>(label)
    popup.popupContent = Composable {
        modifier.align(AlignmentX.Center, AlignmentY.Center).layout(ColumnLayout)
            .background(RoundRectBackground(colors.backgroundMid, sizes.gap))
            .border(RoundRectBorder(colors.primaryVariant, sizes.gap, sizes.borderWidth))
            .padding(sizes.gap)
        var text by remember { mutableStateOf("") }
        Text(label) {
            modifier.margin(sizes.smallGap)
        }
        TextField {
            modifier.text(text)
                .onChange { text = it }
                .size(Grow.Std, FitContent)
                .margin(sizes.smallGap)
                .hint("ваша папка")
        }
        Row {
            modifier.margin(sizes.smallGap)

            Button("Подтвердить") {
                modifier.margin(sizes.smallGap)
                    .onClick {
                        popup.item?.let { item ->
                            if (!renamePopup) {
                                CreateFilePacket(item.treePath + "/" + text.substringBefore('.') + ".story.kts").send()
                            } else {
                                RenameFilePacket(item.treePath, text).send()
                            }
                            scopeSync { IDEGuiV2.fileTree = RequestTreePacket().request().tree }
                            surface.triggerUpdate()
                        }
                        popup.hide()
                    }
            }
            Box(width = Grow.Std) {}
            Button("Отмена") {
                modifier.margin(sizes.smallGap).alignX(AlignmentX.End)
                    .onClick {
                        popup.hide()
                    }
            }
        }
    }
    return popup
}

fun WarningModalPopup(label: String): ItemPopupMenu<TreeNode> {
    val popup = ItemPopupMenu<TreeNode>(label)
    popup.popupContent = Composable {
        modifier.align(AlignmentX.Center, AlignmentY.Center).layout(ColumnLayout)
            .background(RoundRectBackground(colors.backgroundMid, sizes.gap))
            .border(RoundRectBorder(colors.primaryVariant, sizes.gap, sizes.borderWidth))
            .padding(sizes.gap)
        Text(label) {
            modifier.margin(sizes.smallGap)
        }
        Row {
            modifier.margin(sizes.smallGap)

            Button("Подтвердить") {
                modifier.margin(sizes.smallGap)
                    .onClick {
                        popup.item?.let { item ->
                            DeleteFilePacket(item.treePath).send()
                            scopeSync { IDEGuiV2.fileTree = RequestTreePacket().request().tree }
                            surface.triggerUpdate()
                        }
                        popup.hide()
                    }
            }
            Box(width = Grow.Std) {}
            Button("Отмена") {
                modifier.margin(sizes.smallGap).alignX(AlignmentX.End)
                    .onClick {
                        popup.hide()
                    }
            }
        }
    }
    return popup
}

private fun drawIcon(isFolder: Boolean, treeName: String) {
    val folders = mapOf(
        "camera" to "folder_camera",
        "npcs" to "folder_npcs",
        "replays" to "folder_replays",
        "scripts" to "folder_scripts",
        "storyteller_dimension" to "folder_world"
    )
    val file = if (isFolder) {
        folders[treeName] ?: "folder"
    } else when (treeName.substringAfter(".")) {
        "story.kts", "event.kts", "gui.kts", "kts" -> "file_kts"
        "gltf", "glb" -> "model"
        "png", "jpg", "jpeg" -> "file_image"
        "mp3", "ogg", "wav" -> "file_sound"
        "bedrock.json", "efkefc", "efkpkg" -> "file_effect"
        else -> "file"
    }
    val location = "hollowengine:textures/gui/icons/$file.png".rl
}