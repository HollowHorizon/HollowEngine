package ru.hollowhorizon.hollowengine.client.gui.scripting.docking

import de.fabmax.kool.modules.ui2.docking.Dock
import de.fabmax.kool.modules.ui2.docking.DockLayout
import de.fabmax.kool.modules.ui2.docking.DockNode
import de.fabmax.kool.modules.ui2.docking.Dockable
import ru.hollowhorizon.hollowengine.common.events.post
import ru.hollowhorizon.hollowengine.client.gui.scripting.IdeContent
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.TextFileData
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager.fromReadablePath
import ru.hollowhorizon.hollowengine.mixins.kool.DockNodeInvoker

object LayoutLoader {
    val IDE_LAYOUT = "hollowengine.ide.layout"
    val TOOL_LAYOUT = "hollowengine.tool.layout"

    val layoutOrder = LinkedHashSet<String>()
    val LAYOUTS = HashMap<String, Layout>()

    fun loadIdeLayout(dock: Dock) {
        LoadLayoutEvent({ name, layout ->
            LAYOUTS[name] = layout
            layoutOrder.add(name)
        }, dock).post()
        val layoutLoader: (String) -> Dockable? = layout@{ name ->
            if (name.startsWith("scripts/")) IdeContent.openFile(
                name,
                name.fromReadablePath().readBytes(),
                ::TextFileData
            )?.dockable
            else LAYOUTS[name]?.dockable
        }

        val layoutLoaded = DockLayout.loadLayout(IDE_LAYOUT, dock, layoutLoader)

        if (!layoutLoaded) {
            dock.createNodeLayout(listOf("0:leaf"))

            layoutLoader("hollowengine.gui.ide.project_tree")?.let {
                dock.getLeafAtPath("0")?.dock(it)
            }
            layoutLoader("hollowengine.gui.ide.files")?.let {
                dock.getLeafAtPath("0")?.dock(it)
            }
            layoutLoader("hollowengine.gui.ide.recipes")?.let {
                dock.getLeafAtPath("0")?.dock(it)
            }
            layoutLoader("hollowengine.gui.ide.console")?.let {
                dock.getLeafAtPath("0")?.dock(it)
            }
            layoutLoader("hollowengine.gui.ide.autoruns")?.let {
                dock.getLeafAtPath("0")?.dock(it)
            }
        }
    }
}

fun DockNode.insertItem(item: Dockable, slot: DockNode.SlotPosition) =
    (this as DockNodeInvoker).callInsertItem(item, slot)