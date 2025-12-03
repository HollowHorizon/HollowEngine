package ru.hollowhorizon.hollowengine.client.gui.codeblocks

import de.fabmax.kool.modules.ui2.UiScope
import de.fabmax.kool.modules.ui2.docking.Dock
import ru.hollowhorizon.hollowengine.client.gui.scripting.panels.DockPanel

class CodeBlocksPanel(dock: Dock) : DockPanel("hollowengine.gui.ide.codeblocks", dock) {
    override val icon: String
        get() = "hollowengine:textures/gui/icons/console.svg"

    val editor = BlockEditor()

    override fun UiScope.compose() {
        with(editor) {
            EditorLayout()
        }
    }

}