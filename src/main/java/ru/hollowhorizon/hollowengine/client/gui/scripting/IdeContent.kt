package ru.hollowhorizon.hollowengine.client.gui.scripting

import de.fabmax.kool.modules.ui2.DragAndDropContext
import de.fabmax.kool.modules.ui2.docking.DockNode
import net.minecraft.client.Minecraft
import ru.hollowhorizon.hollowengine.client.gui.docs.DocsNode
import ru.hollowhorizon.hollowengine.client.gui.scripting.docking.insertItem
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.DocFileData
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.FileData
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.ImageFileData
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.TextFileData
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.dialog.DialogFileData

object IdeContent {
    val files = HashMap<String, FileData>()
    var fileTree = FileNode.EMPTY
    val dndContext = DragAndDropContext<FileNode>()


    fun openFile(path: String, bytes: ByteArray, type: FileType) {
        val screen = Minecraft.getInstance().screen as? ScriptingEnvironmentScreen ?: return
        val dock = screen.dock

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
                FileType.DIALOG -> DialogFileData(path.substringAfterLast('/'), path)
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

            else -> {}
        }

    }

    fun openDocFile(node: FileNode) {
        val screen = Minecraft.getInstance().screen as? ScriptingEnvironmentScreen ?: return
        val dock = screen.dock

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

    fun openFile(node: FileData) {
        val screen = Minecraft.getInstance().screen as? ScriptingEnvironmentScreen ?: return
        val dock = screen.dock

        files.getOrPut(node.filePath) {
            dock.addDockableSurface(node.dockable, node.surface)
            val fileLeaf = dock.getLeafAtPath("0/1")
            if (fileLeaf != null) fileLeaf.dock(node.dockable)
            else dock.getLeafAtPath("0")?.insertItem(node.dockable, DockNode.SlotPosition.Right)
            node
        }
    }
}