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

        if (isHovered) {
            modifier.background(RoundRectBackground(colors.hoverBg, sizes.smallGap))
        }

        sceneObjectLabel(item, isHovered)
    }

    override fun UiScope.sceneObjectLabel(item: FileNode, isHovered: Boolean): RowScope = Row(width = Grow.Std) {
        if (item.depth > 0) {
            Box(width = sizes.gap * item.depth) {}
        }

        val fgColor = if (isHovered) colors.primary else colors.secondary

        Box {
            modifier
                .alignY(AlignmentY.Center)
                .padding(sizes.smallGap)
                .margin(horizontal = sizes.smallGap)
                .size(sizes.gap, sizes.gap)

            if (item.isFolder) {
                Arrow(isHoverable = false) {
                    modifier
                        .rotation(if (item.isExpanded.use()) ROTATION_DOWN else ROTATION_RIGHT)
                        .align(AlignmentX.Center, AlignmentY.Center)
                        .onClick { item.toggleExpanded() }
                        .size(sizes.gap, sizes.gap)
                }
            }
        }

        Box(width = Grow.Std, height = Grow.Std) {
            Text(item.treeName) {
                modifier
                    .font(sizes.normalText)
                    .alignY(AlignmentY.Center)
                    .textColor(fgColor)
            }
        }
    }
}