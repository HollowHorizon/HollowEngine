package ru.hollowhorizon.hollowengine.client.gui.scripting.files

import de.fabmax.kool.modules.ui2.docking.Dockable
import ru.hollowhorizon.hollowengine.client.gui.scripting.IdeContent
import ru.hollowhorizon.hollowengine.client.gui.scripting.ScriptingEnvironmentOverlay
import ru.hollowhorizon.hollowengine.client.gui.scripting.panels.DockPanel
import ru.hollowhorizon.hollowengine.client.gui.scripting.popup.SubMenuItem
import ru.hollowhorizon.hollowengine.common.codeblocks.modules.icons

abstract class EditorFile(
    val filePath: String,
) : DockPanel("file:[$filePath]", ScriptingEnvironmentOverlay.dock) {
    override val icon = IconHelper.forPath(filePath)

    override fun SubMenuItem<Dockable>.createMenu() {
        item("Save", icons.ICON_45) {
            save()
        }
        item("Close", icons.CLOSE) {
            close()
        }
    }

    abstract fun save()

    override fun close() {
        super.close()
        save()
        IdeContent.files.values.remove(this)
    }
}