package ru.hollowhorizon.hollowengine.client.gui.scripting.tools

import de.fabmax.kool.math.Easing
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.modules.ui2.docking.DockNodeLeaf
import de.fabmax.kool.modules.ui2.docking.Dockable
import ru.hollowhorizon.hollowengine.client.gui.colors.ColorTheme
import ru.hollowhorizon.hollowengine.client.gui.colors.Dimensions
import ru.hollowhorizon.hollowengine.client.gui.scripting.docking.Layout
import ru.hollowhorizon.hollowengine.client.gui.scripting.docking.LayoutLoader
import ru.hollowhorizon.hollowengine.client.gui.scripting.docking.LayoutLoader.layoutOrder
import ru.hollowhorizon.hollowengine.client.gui.scripting.panels.DockPanel
import ru.hollowhorizon.hollowengine.client.kool.minecraft.Image
import ru.hollowhorizon.hollowengine.client.utils.lang

fun UiScope.ToolBar(panel: DockPanel, isLeft: Boolean) = Column(
    Dimensions.PaddingLarge + Dimensions.PaddingSmall + Dimensions.PaddingMedium * 2f,
    Grow.Std
) {
    modifier.backgroundColor(ColorTheme.UI.BackgroundSecondary)
        .margin(top = Dimensions.PaddingNormal)
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
        if (panel != dockNode.dockItemOnTop) animators[panel]?.start(1f)
        dockNode.bringToTop(panel)
    }
}

private val animators = mutableMapOf<Dockable, FloatAnimator>()

fun UiScope.iconButton(
    layout: Layout,
    panel: Dockable,
    tooltip: String? = null,
    toggleState: Boolean = false,
    width: Dimension = Dimensions.PaddingLarge + Dimensions.PaddingSmall + Dimensions.PaddingMedium * 2f,
    height: Dimension = Dimensions.PaddingLarge + Dimensions.PaddingSmall + Dimensions.PaddingMedium * 2f,
    boxBlock: (UiScope.() -> Unit)? = null,
    isLeft: Boolean,
    onClick: (PointerEvent) -> Unit,
) = Box(width, height) {
    val float = animators.getOrPut(panel) { FloatAnimator(1f) }
    val anim = Easing.easeOutQuart(float.updateUsing())

    val tooltipState = remember { TooltipState(0.5) }

    if (toggleState) {
        Box(Dimensions.PaddingNormal, Grow(0.5f * anim)) {
            modifier.background(RoundRectBackground(ColorTheme.Accents.Main, Dimensions.PaddingSmall))
                .border(RoundRectBorder(ColorTheme.Accents.Main.withAlpha(0.33f), Dimensions.PaddingSmall, Dimensions.PaddingNormal))
                .alignY(AlignmentY.Center)
                .margin(start = (-Dimensions.PaddingSmall.value).dp)

            if (!isLeft) modifier.alignX(AlignmentX.End)
        }
    }

    modifier
        .align(AlignmentX.Center, AlignmentY.Center)
        .onClick(onClick)
        .onClick { tooltipState.set(false) }


    Box {

        val isHovered by modifier.hoverable()
        val color by animateColorAsState(
            if (isHovered) ColorTheme.UI.BackgroundElements else ColorTheme.UI.BackgroundSecondary,
            tween(easing = Easing.easeOutQuart)
        )

        modifier.padding(Dimensions.PaddingSmall)
            .margin(Dimensions.PaddingSmall)
            .align(AlignmentX.Center, AlignmentY.Center)

        modifier.background(RoundRectBackground(color, sizes.smallGap))

        Image(layout.icon.toString()) {
            modifier.align(AlignmentX.Center, AlignmentY.Center)
                .size(Dimensions.PaddingLarge, Dimensions.PaddingLarge)
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