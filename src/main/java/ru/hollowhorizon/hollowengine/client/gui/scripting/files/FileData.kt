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
): Composable {
    val dockable = UiDockable(fileName).apply { setFloatingBounds(width = Dp(200f), height = Dp(200f)) }
    val surface = WindowSurface(dockable, ideColors, ideSizes) {
        Column {
            FilesBar(dockable)
            this@FileData()
        }
    }

    abstract fun save()
    open fun close() {}
}