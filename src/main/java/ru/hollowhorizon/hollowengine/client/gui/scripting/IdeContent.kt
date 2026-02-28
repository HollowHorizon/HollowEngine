package ru.hollowhorizon.hollowengine.client.gui.scripting

import de.fabmax.kool.modules.ui2.Dp
import de.fabmax.kool.modules.ui2.docking.DockNode
import de.fabmax.kool.modules.ui2.docking.Dockable
import ru.hollowhorizon.hollowengine.HollowEngine
import ru.hollowhorizon.hollowengine.client.gui.scripting.docking.LayoutLoader
import ru.hollowhorizon.hollowengine.client.gui.scripting.docking.insertItem
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.EditorFile
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.TextFile

object IdeContent {
    val files = HashMap<String, EditorFile>()
    var fileTree = FileNode.EMPTY
    
    init {
        FileTypeRegistry.initialize()
    }

    fun openFile(path: String, bytes: ByteArray): EditorFile? {
        try {
            val file = files.getOrPut(path) {
                val fileType = FileTypeRegistry.findType(path)
                
                val localFile = if (fileType != null) {
                    fileType.resolve(path, bytes)
                } else {
                    tryOpenAsText(path, bytes)
                } ?: throw IllegalStateException("Failed to create file for: $path")
                
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
        } catch (e: Exception) {
            HollowEngine.LOGGER.warn("Can't open $path", e)
            return null
        }
    }

    private fun isBinaryContent(bytes: ByteArray): Boolean {
        if (bytes.isEmpty()) return false
        
        var nullCount = 0
        var nonPrintableCount = 0
        val sampleSize = minOf(bytes.size, 8192)
        
        for (i in 0 until sampleSize) {
            val b = bytes[i].toInt() and 0xFF
            if (b == 0) {
                nullCount++
            } else if (b < 32 && b != 9 && b != 10 && b != 13) {
                nonPrintableCount++
            }
        }
        
        val threshold = sampleSize / 100
        return nullCount > threshold || nonPrintableCount > (sampleSize / 10)
    }

    private fun tryOpenAsText(path: String, bytes: ByteArray): EditorFile? {
        if (isBinaryContent(bytes)) {
            return null
        }
        
        return try {
            TextFile(path, bytes)
        } catch (e: Exception) {
            null
        }
    }

    fun openFile(node: EditorFile) {
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
