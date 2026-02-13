package ru.hollowhorizon.hollowengine.client.gui.scripting

import de.fabmax.kool.modules.ui2.Dp
import de.fabmax.kool.modules.ui2.docking.DockNode
import de.fabmax.kool.modules.ui2.docking.Dockable
import ru.hollowhorizon.hollowengine.client.gui.scripting.docking.LayoutLoader
import ru.hollowhorizon.hollowengine.client.gui.scripting.docking.insertItem
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.IDEFile
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.ScriptFile
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.TextFile
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.codeblocks.CodeBlocksFile
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.prefabs.GLTFFile
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.prefabs.PrefabEditorFile

object IdeContent {
    val files = HashMap<String, IDEFile>()
    var fileTree = FileNode.EMPTY

    private val fileTypes: Map<String, (String, ByteArray) -> IDEFile> = buildMap {
        put(".kts", ::ScriptFile)
        put(".bc", ::CodeBlocksFile)
        put(".txt", ::TextFile)
        put(".gltf") { path, bytes -> GLTFFile(path) }
        put(".fbx") { path, bytes -> GLTFFile(path) }
        put(".glb") { path, bytes -> GLTFFile(path) }
        put(".geo.json") { path, bytes -> GLTFFile(path) }
        put(".entity.prefab", ::PrefabEditorFile)
    }

    fun openFile(path: String, bytes: ByteArray): IDEFile? {

        // Get or Create file
        val file = files.getOrPut(path) {
            val generator = fileTypes.firstNotNullOf { if (path.endsWith(it.key)) it.value else null }
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

    fun openFile(node: IDEFile) {
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