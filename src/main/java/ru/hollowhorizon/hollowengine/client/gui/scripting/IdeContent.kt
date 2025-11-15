package ru.hollowhorizon.hollowengine.client.gui.scripting

import de.fabmax.kool.modules.ui2.Dp
import de.fabmax.kool.modules.ui2.DragAndDropContext
import de.fabmax.kool.modules.ui2.docking.DockNode
import de.fabmax.kool.modules.ui2.docking.Dockable
import net.minecraft.client.Minecraft
import ru.hollowhorizon.hollowengine.client.gui.docs.DocsNode
import ru.hollowhorizon.hollowengine.client.gui.scripting.docking.LayoutLoader
import ru.hollowhorizon.hollowengine.client.gui.scripting.docking.insertItem
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.DocFileData
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.FileData

object IdeContent {
    val files = HashMap<String, FileData>()
    var fileTree = FileNode.EMPTY
    val dndContext = DragAndDropContext<FileNode>()


    fun <T : FileData> openFile(path: String, bytes: ByteArray, generator: (String, ByteArray) -> T): FileData? {

        // Get or Create file
        val file = files.getOrPut(path) {
            val localFile = generator(path, bytes)
            localFile.open()

            val projectLeaf = LayoutLoader.LAYOUTS["hollowengine.gui.ide.project_tree"]?.dockable?.dockedTo?.value
            if (projectLeaf != null) projectLeaf.insertItem(localFile.dockable, DockNode.SlotPosition.Right)
            else {
                localFile.dockable.floatingX.set(Dp(5f))
                localFile.dockable.floatingY.set(Dp.fromPx(ScriptingEnvironmentOverlay.titleBarHeight) + Dp(5f))
            }
            localFile
        }
        return file
    }

    fun openFile(node: FileData) {
        val dock = ScriptingEnvironmentOverlay.dock

        files.getOrPut(node.filePath) {
            node.open()
            val fileLeaf = dock.getLeafAtPath("0/1")
            if (fileLeaf != null) fileLeaf.dock(node.dockable)
            else dock.getLeafAtPath("0")?.insertItem(node.dockable, DockNode.SlotPosition.Right)
            node
        }
    }

    @JvmStatic
    fun contains(dockable: Dockable): Boolean = dockable in files.values.asSequence().map { it.dockable }
}