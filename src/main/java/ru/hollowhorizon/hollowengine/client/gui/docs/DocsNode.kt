package ru.hollowhorizon.hollowengine.client.gui.docs

import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import ru.hollowhorizon.hollowengine.client.gui.scripting.FileNode
import ru.hollowhorizon.hollowengine.client.gui.scripting.IdeContent
import ru.hollowhorizon.hollowengine.client.gui.scripting.theme.IdeTheme
import ru.hollowhorizon.hollowengine.client.gui.scripting.tools.hoverColors
import ru.hollowhorizon.hollowengine.client.utils.lang

class DocsNode(name: String, path: String, val page: Composable? = null) : FileNode(name, path) {
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
                else
                    IdeContent.openDocFile(item)
            }
        }

        val (bgColor, fgColor) = hoverColors(
            0.5f,
            listOf(colors.background, Color("9099ACFF")),
            listOf(IdeTheme.hoveredColors.background, Color("C4CBDAFF"))
        )

        modifier.background(RoundRectBackground(bgColor, sizes.smallGap))
        sceneObjectLabel(item, fgColor)
    }
}