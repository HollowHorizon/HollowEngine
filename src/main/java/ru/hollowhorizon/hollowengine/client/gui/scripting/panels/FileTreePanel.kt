package ru.hollowhorizon.hollowengine.client.gui.scripting.panels

import de.fabmax.kool.modules.ui2.UiScope
import de.fabmax.kool.modules.ui2.docking.Dock
import ru.hollowhorizon.hollowengine.client.gui.scripting.IDEGuiV2
import ru.hollowhorizon.hollowengine.client.gui.scripting.IDEStorage

class FileTreePanel(dock: Dock) : DockPanel("hollowengine.gui.ide.project_tree", dock) {
    override val icon = "hollowengine:textures/gui/icons/code_editor.png"

    override fun UiScope.compose() {
        IDEStorage.fileTree()
    }
}