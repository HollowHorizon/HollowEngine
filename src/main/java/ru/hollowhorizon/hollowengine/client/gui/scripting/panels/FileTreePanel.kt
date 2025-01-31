package ru.hollowhorizon.hollowengine.client.gui.scripting.panels

import de.fabmax.kool.modules.ui2.UiScope
import de.fabmax.kool.modules.ui2.docking.Dock
import ru.hollowhorizon.hollowengine.client.gui.scripting.IDEGuiV2

class FileTreePanel(dock: Dock) : DockPanel("hollowengine.gui.ide.project_tree", dock) {
    override fun UiScope.compose() {
        IDEGuiV2.fileTree()
    }
}