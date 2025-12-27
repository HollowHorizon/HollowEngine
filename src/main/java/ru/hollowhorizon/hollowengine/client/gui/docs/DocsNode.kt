package ru.hollowhorizon.hollowengine.client.gui.docs

import de.fabmax.kool.math.Easing
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import ru.hollowhorizon.hollowengine.client.gui.colors.ColorTheme
import ru.hollowhorizon.hollowengine.client.gui.scripting.FileNode
import ru.hollowhorizon.hollowengine.client.gui.scripting.tools.hoverable
import ru.hollowhorizon.hollowengine.client.utils.lang

class DocsNode(name: String, path: String, var page: Composable? = null) : FileNode(name, path) {
    constructor(path: String, page: Composable? = null) : this(
        "hollowengine.gui.docs.${path.replace('/', '.')}".lang,
        path,
        page
    )

    override fun toggleExpanded() {
        if (!isFolder) return

        // Открываем / Закрываем папку
        isExpanded.set(!isExpanded.value)
    }

    override fun UiScope.sceneObjectItem(item: FileNode) {
        modifier.onClick { evt ->
            if (evt.pointer.isLeftButtonClicked && evt.pointer.leftButtonRepeatedClickCount == 2) {
                if (item.isFolder)
                    item.toggleExpanded()

            }
        }

        val isHovered by modifier.hoverable()
        val bgColor by animateColorAsState(if(isHovered) colors.background else Color("9099ACFF"), tween(easing = Easing.quadRev))
        val fgColor by animateColorAsState(if(isHovered) ColorTheme.UI.BackgroundGeneral else Color("C4CBDAFF"), tween(easing = Easing.quadRev))

        modifier.background(RoundRectBackground(bgColor, sizes.smallGap))
        sceneObjectLabel(item)
    }
}