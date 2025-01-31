package ru.hollowhorizon.hollowengine.client.gui.scripting.files

import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.modules.ui2.docking.UiDockable
import ru.hollowhorizon.hollowengine.client.gui.scripting.IDEGuiV2
import ru.hollowhorizon.hollowengine.client.gui.scripting.ideColors
import ru.hollowhorizon.hollowengine.client.gui.scripting.ideSizes

abstract class FileData(
    val project: IDEGuiV2,
    val fileName: String,
    val filePath: String,
) : Composable {
    val dockable = UiDockable("path.$filePath", IDEGuiV2.dock)
    val surface: UiSurface = WindowSurface(dockable, ideColors, ideSizes) {
        Column(Grow.Std, Grow.Std) {
            FileTitleBar(dockable, onCloseAction = {
                val file = IDEGuiV2.files.values.find { it.dockable == dockable } ?: return@FileTitleBar
                IDEGuiV2.dock.removeDockableSurface(file.surface)
                IDEGuiV2.files.values.remove(file)
            })
            this@FileData()
        }
    }

    abstract fun save()
    open fun close() {}
}