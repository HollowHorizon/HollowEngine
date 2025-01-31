package ru.hollowhorizon.hollowengine.client.gui.scripting.panels

import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.modules.ui2.docking.Dock

class FilesPanel(val text: String, dock: Dock) : DockPanel("hollowengine.gui.ide.files", dock) {
    override fun UiScope.compose() {
        Text(text) {
            modifier.align(AlignmentX.Center, AlignmentY.Center)
                .height(Grow.Std)
        }
    }
}