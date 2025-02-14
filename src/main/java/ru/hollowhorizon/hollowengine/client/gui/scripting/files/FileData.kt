package ru.hollowhorizon.hollowengine.client.gui.scripting.files

import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.modules.ui2.docking.UiDockable
import ru.hollowhorizon.hollowengine.client.gui.scripting.IDEGuiV2
import ru.hollowhorizon.hollowengine.client.gui.scripting.IDEStorage
import ru.hollowhorizon.hollowengine.client.gui.scripting.ideColors
import ru.hollowhorizon.hollowengine.client.gui.scripting.ideSizes

abstract class FileData(
    val project: IDEStorage,
    val fileName: String,
    val filePath: String,
) : Composable {
    val dockable = UiDockable(filePath, IDEStorage.dock)
    val surface: UiSurface = WindowSurface(dockable, ideColors, ideSizes) {
        Column(Grow.Std, Grow.Std) {
            FileTitleBar(dockable, onCloseAction = {
                val file = IDEStorage.files.values.find { it.dockable == dockable } ?: return@FileTitleBar
                IDEStorage.dock.removeDockableSurface(file.surface)
                IDEStorage.files.values.remove(file)
            })
            this@FileData()
        }
    }

    abstract fun save()
    open fun close() {}
}