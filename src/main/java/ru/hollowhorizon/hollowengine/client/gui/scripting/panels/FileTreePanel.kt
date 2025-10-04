package ru.hollowhorizon.hollowengine.client.gui.scripting.panels

import de.fabmax.kool.modules.ui2.AlignmentY
import de.fabmax.kool.modules.ui2.Text
import de.fabmax.kool.modules.ui2.TextField
import de.fabmax.kool.modules.ui2.UiScope
import de.fabmax.kool.modules.ui2.alignY
import de.fabmax.kool.modules.ui2.colors
import de.fabmax.kool.modules.ui2.docking.Dock
import de.fabmax.kool.modules.ui2.hint
import de.fabmax.kool.modules.ui2.margin
import de.fabmax.kool.modules.ui2.onChange
import de.fabmax.kool.modules.ui2.onEnterPressed
import de.fabmax.kool.modules.ui2.remember
import de.fabmax.kool.modules.ui2.width
import org.apache.logging.log4j.LogManager
import ru.hollowhorizon.hollowengine.client.gui.scripting.IdeContent
import ru.hollowhorizon.hollowengine.client.gui.scripting.panels.console.LogMessage
import kotlin.text.isBlank

class FileTreePanel(dock: Dock) : DockPanel("hollowengine.gui.ide.project_tree", dock) {
    override val icon = "hollowengine:textures/gui/icons/code_editor.svg"
    var filter = ""

    override fun UiScope.compose() {
        IdeContent.fileTree.apply {
            draw(filter)
        }
    }

    override fun UiScope.drawHeaderRight() {
        Text("Фильтр:") {
            modifier.alignY(AlignmentY.Center)
        }
        TextField(filter) {
            modifier.margin(horizontal = sizes.gap)
                .colors(lineColor = colors.secondaryVariant, lineColorFocused = colors.secondary)
                .alignY(AlignmentY.Center).hint("Текст или Regex")
                .onEnterPressed { surface.requestFocus(null) }.onChange {
                    filter = it
                }
        }
    }
}