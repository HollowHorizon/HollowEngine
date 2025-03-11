package ru.hollowhorizon.hollowengine.client.gui.scripting

import de.fabmax.kool.modules.ui2.DragAndDropContext
import de.fabmax.kool.modules.ui2.docking.Dock
import de.fabmax.kool.modules.ui2.docking.DockNode
import ru.hollowhorizon.hollowengine.client.gui.docs.DocsNode
import ru.hollowhorizon.hollowengine.client.gui.scripting.docking.insertItem
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.DocFileData
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.FileData
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.ImageFileData
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.TextFileData

object IDEStorage {
    @JvmStatic
    lateinit var dock: Dock


    val files = HashMap<String, FileData>()
    var fileTree = FileNode.EMPTY

    val dndContext = DragAndDropContext<FileNode>()


    fun openFile(path: String, bytes: ByteArray, type: FileType) {
        // Get or Create file
        val file = files.getOrPut(path) {
            val localFile = when (type) {
                FileType.TEXT -> {
                    var text = String(bytes)
                    if(text.isEmpty()) text = "\n"
                    TextFileData(this, path.substringAfterLast('/'), path, text)
                }
                FileType.IMAGE -> ImageFileData(
                    this,
                    path.substringAfterLast('/'),
                    path,
                    bytes
                )
            }
            dock.addDockableSurface(localFile.dockable, localFile.surface)
            val fileLeaf = dock.getLeafAtPath("0/1")
            if (fileLeaf != null) fileLeaf.dock(localFile.dockable)
            else dock.getLeafAtPath("0")?.insertItem(localFile.dockable, DockNode.SlotPosition.Right)
            localFile
        }

        // Update File
        when (type) {
            FileType.IMAGE -> (file as ImageFileData).apply {
                image = bytes
                surface.triggerUpdate()
            }

            FileType.TEXT -> (file as TextFileData).apply {
                setText(String(bytes))
            }
        }

    }

    fun openDocFile(node: FileNode) { // TODO переделать открытие, на открытие панели
        val page = (node as? DocsNode)?.page ?: return
        files.getOrPut(node.treePath) {
            val localFile = DocFileData(node.treeName, node.treePath, page)
            dock.addDockableSurface(localFile.dockable, localFile.surface)
            val fileLeaf = dock.getLeafAtPath("0/1")
            if (fileLeaf != null) fileLeaf.dock(localFile.dockable)
            else dock.getLeafAtPath("0")?.insertItem(localFile.dockable, DockNode.SlotPosition.Right)
            localFile
        }
    }
}