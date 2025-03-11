package ru.hollowhorizon.hollowengine.client.gui.scripting.tools

import de.fabmax.kool.math.Easing
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.modules.ui2.docking.DockNodeLeaf
import de.fabmax.kool.modules.ui2.docking.Dockable
import de.fabmax.kool.util.Color
import ru.hollowhorizon.hc.client.kool.minecraft.Image
import ru.hollowhorizon.hollowengine.client.gui.scripting.docking.LayoutLoader
import ru.hollowhorizon.hollowengine.client.gui.scripting.panels.DockPanel
import ru.hollowhorizon.hollowengine.client.gui.scripting.theme.IdeTheme
import ru.hollowhorizon.hollowengine.client.utils.lang

fun UiScope.ToolBar(panel: DockPanel) = Column(height = Grow.Std) {
    modifier.backgroundColor(colors.background)
    val dockNode = panel.dockable.dockedTo.use() ?: return@Column
    dockNode.dockedItems.sortedBy { it.name }.forEach { dockable ->
        panelButton(
            dockable,
            dockNode,
            LayoutLoader.LAYOUTS[dockable.name]?.icon
                ?: error("Panel ${dockable.name} not registered via LoadLayoutEvent!")
        )
    }
}

fun UiScope.panelButton(panel: Dockable, dockNode: DockNodeLeaf, icon: String) {
    iconButton(icon, panel, panel.name, panel == dockNode.dockItemOnTop) {
        if(panel != dockNode.dockItemOnTop) animators[panel]?.start()
        dockNode.bringToTop(panel)
    }
}

private val animators = mutableMapOf<Dockable, AnimatedFloat>()

fun UiScope.iconButton(
    icon: String,
    panel: Dockable,
    tooltip: String? = null,
    toggleState: Boolean = false,
    tint: Color = colors.onBackground,
    margin: Dp = sizes.smallGap,
    width: Dimension = FitContent,
    height: Dimension = FitContent,
    padding: Dp = sizes.smallGap,
    boxBlock: (UiScope.() -> Unit)? = null,
    onClick: (PointerEvent) -> Unit,
) = Box(width, height) {
    val float = animators.getOrPut(panel) { AnimatedFloat(1f) }
    val anim = Easing.quadRev(float.progressAndUse())

    if (toggleState) {
        Box(sizes.borderWidth * 2, Grow(0.5f * anim)) {
            modifier.background(RoundRectBackground(colors.onBackground, sizes.smallGap * 0.5f))
                .alignY(AlignmentY.Center)
        }
    }

    modifier
        .align(AlignmentX.Center, AlignmentY.Center)
        .onClick(onClick)


    Box {
        var isHovered by remember(false)
        modifier.onEnter { isHovered = true }.onExit { isHovered = false }
            .margin(sizes.smallGap)
            .padding(sizes.smallGap)

        if (isHovered) modifier.background(
            RoundRectBackground(
                IdeTheme.hoveredColors.background,
                sizes.smallGap
            )
        )

        Image(icon) {
            modifier.align(AlignmentX.Center, AlignmentY.Center)
                .size(28.dp, 28.dp)
        }

        tooltip?.let { text ->
            Tooltip(remember { TooltipState(0.5) }) {
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