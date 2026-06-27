package ru.hollowhorizon.hollowengine.client.gui.scripting

import de.fabmax.kool.Clipboard
import de.fabmax.kool.math.Vec2f
import de.fabmax.kool.modules.ui2.Composable
import de.fabmax.kool.modules.ui2.UiScope
import de.fabmax.kool.modules.ui2.remember
import net.minecraft.client.Minecraft
import ru.hollowhorizon.hollowengine.client.gui.scripting.popup.ItemPopupMenu
import ru.hollowhorizon.hollowengine.client.gui.scripting.popup.SubMenuItem
import ru.hollowhorizon.hollowengine.client.utils.lang
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager.fromReadablePath
import ru.hollowhorizon.hollowengine.common.util.DesktopUtil
import ru.hollowhorizon.hollowengine.generated.Assets.Hollowengine.Textures.Gui.Icons.ADD
import ru.hollowhorizon.hollowengine.generated.Assets.Hollowengine.Textures.Gui.Icons.COPY
import ru.hollowhorizon.hollowengine.generated.Assets.Hollowengine.Textures.Gui.Icons.CREATE_FILE
import ru.hollowhorizon.hollowengine.generated.Assets.Hollowengine.Textures.Gui.Icons.CREATE_FOLDER
import ru.hollowhorizon.hollowengine.generated.Assets.Hollowengine.Textures.Gui.Icons.CUT
import ru.hollowhorizon.hollowengine.generated.Assets.Hollowengine.Textures.Gui.Icons.FILE_KTS
import ru.hollowhorizon.hollowengine.generated.Assets.Hollowengine.Textures.Gui.Icons.FOLDER
import ru.hollowhorizon.hollowengine.generated.Assets.Hollowengine.Textures.Gui.Icons.GITHUB
import ru.hollowhorizon.hollowengine.generated.Assets.Hollowengine.Textures.Gui.Icons.PASTE
import ru.hollowhorizon.hollowengine.generated.Assets.Hollowengine.Textures.Gui.Icons.REMOVE
import ru.hollowhorizon.hollowengine.generated.Assets.Hollowengine.Textures.Gui.Icons.RENAME

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
            remember { EditPopup("hollowengine.gui.ide.popups.create_folder", "hollowengine.gui.ide.popups.create_folder_hint", ::createFolder) }
        createFolderPopup()
        createFilePopup = remember { EditPopup("hollowengine.gui.ide.popups.create_file", "hollowengine.gui.ide.popups.create_file_hint", ::createFile) }
        createFilePopup()
        renamePopup = remember { EditPopup("hollowengine.gui.ide.popups.rename", "hollowengine.gui.ide.popups.rename_hint", ::rename) }
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
                subMenu(ACTIONS("create"), ADD) {
                    item(ACTIONS("create.folder"), CREATE_FOLDER) {
                        createFolderPopup.show(Vec2f.ZERO, SubMenuItem {}, node)
                    }
                    if (node.treePath.startsWith("scripts")) {
                        subMenu(ACTIONS("create.script"), CREATE_FILE) {
                            item("hollowengine.gui.ide.actions.create.script.simple".lang, FILE_KTS) {
                                fileExtension = ".ktr"
                                createFilePopup.show(Vec2f.ZERO, SubMenuItem {}, node)
                            }
                        }
                    }
                }
            } else {
                item(ACTIONS("open"), FILE_KTS) {
                    IdeContent.openFile(node.treePath, node.treePath.fromReadablePath().readBytes())
                }
            }
            divider()
            item(ACTIONS("copy"), COPY) {
                copySource = it.treePath
                deleteOriginal = false
            }
            item(ACTIONS("cut"), CUT) {
                copySource = it.treePath
                deleteOriginal = true
            }
            item(ACTIONS("paste"), PASTE) {
                if (!it.isFolder) return@item

                if (copySource.isNotEmpty()) {
                    val sourceFile = copySource.fromReadablePath()
                    val targetDir = it.treePath.fromReadablePath()

                    if (!sourceFile.exists()) {
                        copySource = ""
                        deleteOriginal = false
                        IdeContent.fileTree.update()
                        return@item
                    }

                    val destination = targetDir.resolve(sourceFile.name)
                    if (sourceFile.absoluteFile == destination.absoluteFile) return@item

                    runCatching {
                        if (sourceFile.isDirectory) {
                            sourceFile.copyRecursively(destination, overwrite = true)
                        } else {
                            sourceFile.copyTo(destination, overwrite = true)
                        }
                        if (deleteOriginal) sourceFile.deleteRecursively()
                    }.onFailure {
                        return@item
                    }
                }
                if (deleteOriginal) {
                    copySource = ""
                    deleteOriginal = false
                }
                IdeContent.fileTree.update()
            }
            divider()
            var hasAny = false
            if (node.treePath.startsWith("assets/")) {
                item(
                    ACTIONS("copy_as_path"),
                    COPY
                ) {
                    Clipboard.copyToClipboard(node.treePath.substringAfter("assets/").replaceFirst('/', ':'))
                }
                hasAny = true
            }
            if (Minecraft.getInstance().isLocalServer) {
                item(
                    ACTIONS("open_in_explorer"),
                    FOLDER
                ) {
                    DesktopUtil.openInExplorer(node.treePath.fromReadablePath())
                }
                hasAny = true
            }
            if (hasAny) divider()
            subMenu(ACTIONS("github"), GITHUB) {}
            divider()
            item(ACTIONS("rename"), RENAME) {
                renamePopup.show(Vec2f.ZERO, SubMenuItem {}, node)
            }
            item(ACTIONS("delete"), REMOVE) {
                deletePopup.show(Vec2f.ZERO, SubMenuItem {}, node)
                // Folder refresh happens via close / reopen
            }
        }, node)
    }

    companion object {
        val ACTIONS: (String) -> String = { "hollowengine.gui.ide.actions.${it}".lang }
    }
}
