package ru.hollowhorizon.hollowengine.client.gui.scripting

import de.fabmax.kool.math.Vec2f
import de.fabmax.kool.modules.ui2.Composable
import de.fabmax.kool.modules.ui2.UiScope
import de.fabmax.kool.modules.ui2.remember
import ru.hollowhorizon.hollowengine.client.gui.scripting.popup.ItemPopupMenu
import ru.hollowhorizon.hollowengine.client.gui.scripting.popup.SubMenuItem

class FilePopup : Composable {
    private lateinit var filePopup: ItemPopupMenu<FileNode>

    private lateinit var createFolderPopup: ItemPopupMenu<FileNode>
    private lateinit var createFilePopup: ItemPopupMenu<FileNode>
    private lateinit var renamePopup: ItemPopupMenu<FileNode>
    private lateinit var deletePopup: ItemPopupMenu<FileNode>

    private var fileExtension = ".kts"

    override fun UiScope.compose() {
        createFolderPopup = remember { EditPopup("hollowengine.gui.ide.popups.create_folder", "Имя папки", ::createFolder) }
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

    private fun createFolder(item: FileNode, name: String) = CreateFilePacket(item.treePath + "/" + name).send()
    private fun createFile(item: FileNode, name: String) = CreateFilePacket(item.treePath + "/" + name + fileExtension).send()
    private fun rename(item: FileNode, newName: String) = RenameFilePacket(item.treePath, newName).send()
    private fun delete(item: FileNode) = DeleteFilePacket(item.treePath).send()

    fun show(node: FileNode, position: Vec2f) {
        filePopup.hide()
        filePopup.show(position, SubMenuItem {
            if (node.isFolder) {
                subMenu("Создать", "hollowengine:textures/gui/icons/add.png") {
                    item("Папка", "hollowengine:textures/gui/icons/create_folder.png") {
                        createFolderPopup.show(Vec2f.ZERO, SubMenuItem {}, node)
                    }
                    subMenu("Скрипт", "hollowengine:textures/gui/icons/create_file.png") {
                        item("Сюжетное событие", "hollowengine:textures/gui/icons/file_kts.png") {
                            fileExtension = ".story.kts"
                            createFilePopup.show(Vec2f.ZERO, SubMenuItem {}, node)
                        }
                        item("Интерфейс", "hollowengine:textures/gui/icons/file_kts.png") {
                            fileExtension = ".gui.kts"
                            createFilePopup.show(Vec2f.ZERO, SubMenuItem {}, node)
                        }
                        item("Обработчик событий", "hollowengine:textures/gui/icons/file_kts.png") {
                            fileExtension = ".event.kts"
                            createFilePopup.show(Vec2f.ZERO, SubMenuItem {}, node)
                        }
                    }
                }
            } else {
                item("Открыть", "hollowengine:textures/gui/icons/file_kts.png") {
                    RequestFilePacket(node.treePath).send()
                }
            }
            divider()
            item("Переименовать", "hollowengine:textures/gui/icons/rename.png") {
                renamePopup.show(Vec2f.ZERO, SubMenuItem {}, node)
            }
            item("Удалить", "hollowengine:textures/gui/icons/remove.png") {
                deletePopup.show(Vec2f.ZERO, SubMenuItem {}, node)
            }
        }, node)
    }
}