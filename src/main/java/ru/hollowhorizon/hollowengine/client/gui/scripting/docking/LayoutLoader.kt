package ru.hollowhorizon.hollowengine.client.gui.scripting.docking

import de.fabmax.kool.modules.ui2.docking.Dock
import de.fabmax.kool.modules.ui2.docking.DockLayout
import de.fabmax.kool.modules.ui2.docking.DockNode
import de.fabmax.kool.modules.ui2.docking.Dockable
import ru.hollowhorizon.hollowengine.client.gui.scripting.IdeContent
import ru.hollowhorizon.hollowengine.common.events.post
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
                name.fromReadablePath().readBytes()
            )?.dockable
            else LAYOUTS[name]?.dockable
        }

        DockLayout.loadLayout(IDE_LAYOUT, dock, layoutLoader)

    }

    @JvmStatic
    fun contains(dockable: Dockable): Boolean = dockable in LAYOUTS.values.asSequence().map { it.dockable }
}

fun DockNode.insertItem(item: Dockable, slot: DockNode.SlotPosition) =
    (this as DockNodeInvoker).callInsertItem(item, slot)