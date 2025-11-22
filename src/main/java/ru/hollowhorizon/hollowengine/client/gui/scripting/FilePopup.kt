package ru.hollowhorizon.hollowengine.client.gui.scripting

import de.fabmax.kool.Clipboard
import de.fabmax.kool.math.Vec2f
import de.fabmax.kool.modules.ui2.Composable
import de.fabmax.kool.modules.ui2.UiScope
import de.fabmax.kool.modules.ui2.remember
import net.minecraft.client.Minecraft
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.TextFileData
import ru.hollowhorizon.hollowengine.client.gui.scripting.popup.ItemPopupMenu
import ru.hollowhorizon.hollowengine.client.gui.scripting.popup.SubMenuItem
import ru.hollowhorizon.hollowengine.client.utils.lang
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager.fromReadablePath
import ru.hollowhorizon.hollowengine.common.util.DesktopUtil

class FilePopup : Composable {
    private lateinit var filePopup: ItemPopupMenu<FileNode>

    private lateinit var createFolderPopup: ItemPopupMenu<FileNode>
    private lateinit var createFilePopup: ItemPopupMenu<FileNode>
    private lateinit var renamePopup: ItemPopupMenu<FileNode>
    private lateinit var deletePopup: ItemPopupMenu<FileNode>

    private var copySource = ""
    private var deleteOriginal = false
    private var fileExtension = ".kts"

    override fun UiScope.compose() {
        createFolderPopup =
            remember { EditPopup("hollowengine.gui.ide.popups.create_folder", "Имя папки", ::createFolder) }
        createFolderPopup()
        createFilePopup = remember { EditPopup("hollowengine.gui.ide.popups.create_file", "Имя файла", ::createFile) }
        createFilePopup()
        renamePopup = remember { EditPopup("hollowengine.gui.ide.popups.rename", "Новое название", ::rename) }
        renamePopup()
        deletePopup = remember { WarningModalPopup("hollowengine.gui.ide.popups.delete", ::delete) }
        deletePopup()

        filePopup = remember { ItemPopupMenu("scene-item-popup") }
        filePopup()
    }

    private fun createFolder(item: FileNode, name: String) {
        item.treePath.fromReadablePath().resolve(name).mkdirs()
        item.update()
    }

    private fun createFile(item: FileNode, name: String) {
        item.treePath.fromReadablePath().resolve(name + fileExtension).createNewFile()
        item.update()
    }

    private fun rename(item: FileNode, newName: String) {
        item.treePath.fromReadablePath().renameTo(item.treePath.fromReadablePath().parentFile.resolve(newName))
        item.parent?.update()
    }

    private fun delete(item: FileNode) {
        item.treePath.fromReadablePath().deleteRecursively()
        item.parent?.update()
    }

    fun show(node: FileNode, position: Vec2f) {
        filePopup.hide()
        filePopup.show(Vec2f(position), SubMenuItem {
            if (node.isFolder) {
                subMenu(ACTIONS("create"), "hollowengine:textures/gui/icons/add.png") {
                    item(ACTIONS("create.folder"), "hollowengine:textures/gui/icons/create_folder.png") {
                        createFolderPopup.show(Vec2f.ZERO, SubMenuItem {}, node)
                    }
                    if (node.treePath.startsWith("scripts")) {
                        subMenu(ACTIONS("create.script"), "hollowengine:textures/gui/icons/create_file.png") {

                        }
                    }
                }
            } else {
                item(ACTIONS("open"), "hollowengine:textures/gui/icons/file_kts.png") {
                    IdeContent.openFile(node.treePath, node.treePath.fromReadablePath().readBytes(), ::TextFileData)
                }
            }
            divider()
            item(ACTIONS("copy"), "hollowengine:textures/gui/icons/copy.png") {
                copySource = it.treePath
                deleteOriginal = false
            }
            item(ACTIONS("cut"), "hollowengine:textures/gui/icons/cut.png") {
                copySource = it.treePath
                deleteOriginal = true
            }
            item(ACTIONS("paste"), "hollowengine:textures/gui/icons/paste.png") {
                if (!it.isFolder) return@item

                val target = it.treePath

                if (copySource.isNotEmpty()) {
                    copySource.fromReadablePath().copyTo(target.fromReadablePath(), true)
                    if (deleteOriginal) copySource.fromReadablePath().deleteRecursively()
                }
                if (deleteOriginal) copySource = ""
            }
            divider()
            if (node.treePath.startsWith("assets/")) item(
                ACTIONS("copy_as_path"),
                "hollowengine:textures/gui/icons/copy.png"
            ) {
                Clipboard.copyToClipboard(node.treePath.substringAfter("assets/").replaceFirst('/', ':'))
            }
            if (Minecraft.getInstance().isLocalServer) item(
                ACTIONS("open_in_explorer"),
                "hollowengine:textures/gui/icons/explorer.png"
            ) {
                DesktopUtil.openInExplorer(node.treePath.fromReadablePath())
            }
            divider()
            subMenu(ACTIONS("github"), "hollowengine:textures/gui/icons/github.png") {}
            divider()
            item(ACTIONS("rename"), "hollowengine:textures/gui/icons/rename.png") {
                renamePopup.show(Vec2f.ZERO, SubMenuItem {}, node)
            }
            item(ACTIONS("delete"), "hollowengine:textures/gui/icons/remove.png") {
                deletePopup.show(Vec2f.ZERO, SubMenuItem {}, node)
                // Немного халтурный способ обновления папки - закрыть и открыть
            }
        }, node)
    }

    companion object {
        val ACTIONS: (String) -> String = { "hollowengine.gui.ide.actions.${it}".lang }
    }
}