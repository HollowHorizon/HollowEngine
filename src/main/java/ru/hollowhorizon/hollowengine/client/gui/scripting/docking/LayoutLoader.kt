package ru.hollowhorizon.hollowengine.client.gui.scripting.docking

import de.fabmax.kool.modules.ui2.docking.Dock
import de.fabmax.kool.modules.ui2.docking.DockLayout
import de.fabmax.kool.modules.ui2.docking.DockNode
import de.fabmax.kool.modules.ui2.docking.Dockable
import ru.hollowhorizon.hollowengine.mixins.kool.DockNodeInvoker

object LayoutLoader {
    val IDE_LAYOUT = "hollowengine.ide.layout"

    fun loadIdeLayout(dock: Dock, layoutLoader: (String) -> Dockable?) {
        val layoutLoaded = DockLayout.loadLayout(IDE_LAYOUT, dock, layoutLoader)

        if (!layoutLoaded) {
            dock.createNodeLayout(
                listOf(
                    "0:row",
                    "0/0:leaf",
                    "0/1:leaf"
                )
            )

            layoutLoader("hollowengine.gui.ide.docs")?.let {
                dock.getLeafAtPath("0/0")?.dock(it)
            }
            layoutLoader("hollowengine.gui.ide.project_tree")?.let {
                dock.getLeafAtPath("0/0")?.dock(it)
            }
            layoutLoader("hollowengine.gui.ide.files")?.let {
                dock.getLeafAtPath("0/1")?.dock(it)
            }
        }
    }
}

fun DockNode.insertItem(item: Dockable, slot: DockNode.SlotPosition) = (this as DockNodeInvoker).callInsertItem(item, slot)