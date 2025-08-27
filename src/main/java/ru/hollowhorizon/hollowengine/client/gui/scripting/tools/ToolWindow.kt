package ru.hollowhorizon.hollowengine.client.gui.scripting.tools

import de.fabmax.kool.math.Easing
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.modules.ui2.docking.DockNodeLeaf
import de.fabmax.kool.modules.ui2.docking.Dockable
import ru.hollowhorizon.hollowengine.client.kool.minecraft.Image
import ru.hollowhorizon.hollowengine.client.gui.scripting.docking.Layout
import ru.hollowhorizon.hollowengine.client.gui.scripting.docking.LayoutLoader
import ru.hollowhorizon.hollowengine.client.gui.scripting.docking.LayoutLoader.layoutOrder
import ru.hollowhorizon.hollowengine.client.gui.scripting.panels.DockPanel
import ru.hollowhorizon.hollowengine.client.gui.scripting.theme.IdeTheme
import ru.hollowhorizon.hollowengine.client.utils.lang

fun UiScope.ToolBar(panel: DockPanel, isLeft: Boolean) = Column(height = Grow.Std) {
    modifier.backgroundColor(colors.background)
    val dockNode = panel.dockable.dockedTo.use() ?: return@Column
    dockNode.dockedItems.sortedBy { layoutOrder.indexOf(it.name) }.forEach { dockable ->
        panelButton(
            dockable,
            dockNode,
            LayoutLoader.LAYOUTS[dockable.name]
                ?: error("Panel ${dockable.name} not registered via LoadLayoutEvent!"),
            isLeft
        )
    }
}

fun UiScope.panelButton(panel: Dockable, dockNode: DockNodeLeaf, layout: Layout, isLeft: Boolean) {
    iconButton(layout, panel, panel.name, panel == dockNode.dockItemOnTop, isLeft = isLeft) {
        if (panel != dockNode.dockItemOnTop) animators[panel]?.start()
        dockNode.bringToTop(panel)
    }
}

private val animators = mutableMapOf<Dockable, AnimatedFloat>()

fun UiScope.iconButton(
    layout: Layout,
    panel: Dockable,
    tooltip: String? = null,
    toggleState: Boolean = false,
    width: Dimension = FitContent,
    height: Dimension = FitContent,
    boxBlock: (UiScope.() -> Unit)? = null,
    isLeft: Boolean,
    onClick: (PointerEvent) -> Unit,
) = Box(width, height) {
    val float = animators.getOrPut(panel) { AnimatedFloat(1f) }
    val anim = Easing.quadRev(float.progressAndUse())

    val tooltipState = remember { TooltipState(0.5) }

    if (toggleState) {
        Box(sizes.borderWidth * 2, Grow(0.5f * anim)) {
            modifier.background(RoundRectBackground(colors.onBackground, sizes.smallGap * 0.5f))
                .alignY(AlignmentY.Center)

            if (!isLeft) modifier.alignX(AlignmentX.End)
        }
    }

    modifier
        .align(AlignmentX.Center, AlignmentY.Center)
        .onClick(onClick)
        .onClick { tooltipState.set(false) }


    Box {
        val color =
            hoverColors(color = colors.background, hoverColor = IdeTheme.hoveredColors.background)
        if (toggleState) color.set(IdeTheme.hoveredColors.background)

        modifier
            .margin(sizes.smallGap)
            .padding(sizes.smallGap)

        modifier.background(RoundRectBackground(color, sizes.smallGap))

        Image(layout.icon) {
            modifier.align(AlignmentX.Center, AlignmentY.Center)
                .size(28.dp, 28.dp)
        }

        tooltip?.let { text ->
            Tooltip(tooltipState) {
                modifier.layout(CellLayout)
                    .background(UiRenderer { node ->
                        node.apply {
                            val backgroundColor = colors.backgroundVariant
                            val border = colors.primaryVariant

                            getUiPrimitives(UiSurface.LAYER_BACKGROUND).apply {
                                localRoundRect(0f, 0f, widthPx, heightPx, sizes.smallGap.px, backgroundColor)
                                localRoundRectBorder(
                                    0f,
                                    0f,
                                    widthPx,
                                    heightPx,
                                    sizes.smallGap.px,
                                    sizes.borderWidth.px,
                                    border
                                )
                            }
                        }
                    })

                Text(text.lang) {
                    modifier.padding(sizes.smallGap)
                }
            }
        }
    }

    boxBlock?.invoke(this)
}