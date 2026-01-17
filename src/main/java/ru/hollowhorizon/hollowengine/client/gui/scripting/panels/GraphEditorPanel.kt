package ru.hollowhorizon.hollowengine.client.gui.scripting.panels

import de.fabmax.kool.modules.ui2.UiScope
import de.fabmax.kool.modules.ui2.docking.Dock
import ru.hollowhorizon.hollowengine.client.gui.animations.GraphEditor
import ru.hollowhorizon.hollowengine.generated.Assets

class GraphEditorPanel(dock: Dock) : DockPanel("hollowengine.gui.ide.graph", dock) {
    val editor = GraphEditor()
    override val icon = Assets.Hollowengine.Textures.Gui.Icons.GRAPH

    override fun UiScope.compose() {
        editor.EditorLayout()
    }

}