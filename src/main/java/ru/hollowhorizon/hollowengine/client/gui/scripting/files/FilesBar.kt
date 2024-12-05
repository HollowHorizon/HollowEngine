package ru.hollowhorizon.hollowengine.client.gui.scripting.files

import de.fabmax.kool.editor.ui.backgroundMid
import de.fabmax.kool.editor.ui.hoverBg
import de.fabmax.kool.editor.ui.lineHeight
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.modules.ui2.docking.Dockable
import de.fabmax.kool.modules.ui2.docking.UiDockable
import de.fabmax.kool.util.Color
import ru.hollowhorizon.hollowengine.client.gui.scripting.CLOSE
import ru.hollowhorizon.hollowengine.client.gui.scripting.FILE
import ru.hollowhorizon.hollowengine.client.gui.scripting.IDEGuiV2
import ru.hollowhorizon.hollowengine.client.gui.scripting.KOTLIN


fun UiScope.FilesBar(window: UiDockable) {
    window.dockedTo.use()?.let { dockNode ->

        LazyList(
            width = Grow.Std,
            listOrientation = ListOrientation.Horizontal,
            containerModifier = { it.backgroundColor(null) },
            withHorizontalScrollbar = true,
            withVerticalScrollbar = false,
            hScrollbarModifier = { it.height(10.dp).margin(5.dp).alignY(AlignmentY.Top) }
        ) {
            modifier
                .backgroundColor(colors.backgroundMid)
                .padding(top = sizes.smallGap * 0.5f)
                .margin(start = sizes.smallGap)

            var hoverIndex by remember(-1)

            dockNode.dockedItems.forEachIndexed { index, panel ->
                Row(height = Grow.Std) {
                    modifier.onHover { hoverIndex = index }.onExit { hoverIndex = -1 }
                        .onClick { dockNode.bringToTop(panel) }
                        .background(RoundRectBackground(colors.hoverBg, sizes.smallGap))
                        .margin(start = sizes.smallGap, end = sizes.smallGap, top = sizes.smallGap)

                    if (hoverIndex == index) {
                        modifier.background(RoundRectBackground(colors.hoverBg.withAlpha(0.75f), sizes.smallGap))
                    }

                    if (panel == window) {
                        modifier.background(RoundRectBackground(colors.hoverBg.withAlpha(0.5f), sizes.smallGap))
                    }

                    val icon = when {
                        panel.name.endsWith(".kts") -> KOTLIN
                        else -> FILE
                    }
                    Box {
                        modifier.alignY(AlignmentY.Center)
                        Image(icon) {
                            modifier.margin(horizontal = 10.dp).size(sizes.lineHeight, sizes.lineHeight)
                                .imageSize(ImageSize.Stretch)
                        }
                    }
                    Box(width = Grow.Std, height = Grow.Std) {
                        modifier.alignY(AlignmentY.Center).margin(horizontal = sizes.smallGap)
                        Text(panel.name) {
                            modifier.textColor(if (hoverIndex == index || panel == window) colors.primary else colors.secondary)
                        }
                    }
                    drawCloseButton(panel)
                }
            }
        }
    }
}

private fun UiScope.drawCloseButton(panel: Dockable) {
    var isHovered by remember(false)

    Box {
        modifier.alignY(AlignmentY.Center).onEnter { isHovered = true }.onExit { isHovered = false }
        if (isHovered) {
            modifier.background(RoundRectBackground(colors.hoverBg.mulRgb(1.25f), sizes.smallGap))
        }

        Image(CLOSE) {
            modifier.margin(3.dp).size(sizes.lineHeight, sizes.lineHeight)
                .imageSize(ImageSize.Stretch)
                .onClick {
                    val file = IDEGuiV2.files.find { it.dockable == panel } ?: return@onClick

                    IDEGuiV2.dock.removeDockableSurface(file.surface)
                    IDEGuiV2.files.remove(file)

                    if (IDEGuiV2.files.isEmpty()) {
                        IDEGuiV2.dock.createNodeLayout(
                            listOf(
                                "0:row",
                                "0/0:leaf",
                                "0/1:leaf"
                            )
                        )
                    }
                }

            if (isHovered) modifier.tint(Color.LIGHT_RED)
        }
    }
}