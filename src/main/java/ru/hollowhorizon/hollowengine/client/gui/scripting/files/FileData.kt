package ru.hollowhorizon.hollowengine.client.gui.scripting.files

import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.modules.ui2.docking.UiDockable
import ru.hollowhorizon.hollowengine.client.gui.scripting.IDEGuiV2
import ru.hollowhorizon.hollowengine.client.gui.scripting.IDEGuiV2.projectDock
import ru.hollowhorizon.hollowengine.client.gui.scripting.ideColors
import ru.hollowhorizon.hollowengine.client.gui.scripting.ideSizes

abstract class FileData(
    val project: IDEGuiV2,
    val fileName: String,
    val filePath: String,
): Composable {
    val dockable = UiDockable(fileName, IDEGuiV2.dock)
    val surface: UiSurface = WindowSurface(dockable, ideColors, ideSizes) {
        Column(Grow.Std, Grow.Std) {
            FileTitleBar(dockable, onCloseAction = {
                val file = IDEGuiV2.files.find { it.dockable == dockable } ?: return@FileTitleBar
                IDEGuiV2.dock.removeDockableSurface(file.surface)
                IDEGuiV2.files.remove(file)

                if(IDEGuiV2.files.isEmpty()) {
                    IDEGuiV2.dock.createNodeLayout(
                        listOf(
                            "0:row",
                            "0/0:leaf",
                            "0/1:leaf"
                        )
                    )
                    IDEGuiV2.dock.getLeafAtPath("0/0")?.dock(projectDock)
                }
            })
            this@FileData()
        }
    }

    abstract fun save()
    open fun close() {}
}