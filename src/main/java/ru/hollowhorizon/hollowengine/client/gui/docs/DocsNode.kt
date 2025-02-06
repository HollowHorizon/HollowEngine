package ru.hollowhorizon.hollowengine.client.gui.docs

import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.modules.ui2.ArrowScope.Companion.ROTATION_DOWN
import de.fabmax.kool.modules.ui2.ArrowScope.Companion.ROTATION_RIGHT
import ru.hollowhorizon.hollowengine.client.gui.kool.hoverBg
import ru.hollowhorizon.hollowengine.client.gui.scripting.FileNode
import ru.hollowhorizon.hollowengine.client.gui.scripting.IDEGuiV2
import ru.hollowhorizon.hollowengine.client.utils.lang

class DocsNode(name: String, path: String, val page: Composable? = null) : FileNode(name, path) {
    constructor(path: String, page: Composable? = null) : this("hollowengine.gui.docs.${path.replace('/', '.')}".lang, path, page)

    companion object {
        val SPACING = Dp(25f)
    }

    override fun toggleExpanded() {
        if (!isFolder) return

        // Открываем / Закрываем папку
        isExpanded.set(!isExpanded.value)
    }

    override fun UiScope.sceneObjectItem(item: FileNode, isHovered: Boolean) {
        modifier
            .onClick { evt ->
                if (evt.pointer.isLeftButtonClicked) {
                    if (item.isFolder && evt.pointer.leftButtonRepeatedClickCount == 2) {
                        item.toggleExpanded()
                    } else {
                        IDEGuiV2.openDocFile(item)
                    }
                }
            }
            .margin(horizontal = sizes.smallGap)
            .padding(horizontal = sizes.smallGap)

        if (isHovered) {
            modifier.background(RoundRectBackground(colors.hoverBg, sizes.smallGap))
        }

        sceneObjectLabel(item, isHovered)
    }

    override fun UiScope.sceneObjectLabel(item: FileNode, isHovered: Boolean): RowScope = Row(width = Grow.Std) {
        modifier.margin(vertical = 10.dp)
        if (item.depth > 0) {
            var depth = item.depth
            if (!item.isFolder) depth += 1
            Box(width = SPACING * depth) {}
        }

        val fgColor = if (isHovered) colors.primary else colors.secondary

        Box {
            modifier
                .alignY(AlignmentY.Center)
                .margin(end = 7.dp)

            if (item.isFolder) {
                Arrow(isHoverable = false) {
                    modifier
                        .rotation(if (item.isExpanded.use()) ROTATION_DOWN else ROTATION_RIGHT)
                        .align(AlignmentX.Center, AlignmentY.Center)
                        .onClick { item.toggleExpanded() }
                        .size(SPACING, SPACING)
                }
            }
        }

        val largerFont = remember { sizes.normalText.derive(40f) }

        Box(width = Grow.Std, height = Grow.Std) {
            Text(item.treeName) {
                modifier
                    .font(largerFont)
                    .alignY(AlignmentY.Center)
                    .textColor(fgColor)
            }
        }
    }
}