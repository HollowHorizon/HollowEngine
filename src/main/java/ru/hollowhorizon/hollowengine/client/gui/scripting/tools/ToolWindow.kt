package ru.hollowhorizon.hollowengine.client.gui.scripting.tools

import de.fabmax.kool.Assets
import de.fabmax.kool.loadImage2d
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.modules.ui2.docking.DockNodeLeaf
import de.fabmax.kool.modules.ui2.docking.Dockable
import de.fabmax.kool.pipeline.Texture2d
import de.fabmax.kool.util.Color
import ru.hollowhorizon.hollowengine.client.gui.kool.baseSize
import ru.hollowhorizon.hollowengine.client.gui.scripting.IDEGuiV2
import ru.hollowhorizon.hollowengine.client.gui.scripting.panels
import ru.hollowhorizon.hollowengine.client.gui.scripting.panels.DockPanel
import ru.hollowhorizon.hollowengine.client.gui.scripting.popup.iconImage
import ru.hollowhorizon.hollowengine.client.utils.lang

val Sizes.panelBarWidth: Dp get() = baseSize - borderWidth

fun UiScope.ToolBar(panel: DockPanel) = Column(width = sizes.panelBarWidth, height = Grow.Std) {
    modifier
        .backgroundColor(colors.background)
        .padding(sizes.smallGap)
    val dockNode = panel.dockable.dockedTo.use() ?: return@Column
    dockNode.dockedItems.forEach { dockable ->
        panelButton(dockable, dockNode, panels[dockable]?.icon ?: return@forEach)
    }
}

fun UiScope.panelButton(panel: Dockable, dockNode: DockNodeLeaf, icon: String) {
    iconButton(remember {
        Texture2d {
            Assets.loadImage2d(icon).getOrThrow()
        }
    }, panel.name, panel == dockNode.dockItemOnTop) {
        dockNode.bringToTop(panel)
    }
}

fun UiScope.iconButton(
    icon: Texture2d,
    tooltip: String? = null,
    toggleState: Boolean = false,
    tint: Color = colors.onBackground,
    margin: Dp = sizes.smallGap,
    width: Dimension = FitContent,
    height: Dimension = FitContent,
    padding: Dp = sizes.smallGap * 0.5f,
    boxBlock: (UiScope.() -> Unit)? = null,
    onClick: (PointerEvent) -> Unit
) = Box(width, height) {
    var isHovered by remember(false)
    var isClickFeedback by remember(false)

    val bgColor = when {
        isClickFeedback -> colors.secondary
        toggleState || isHovered -> colors.secondaryAlpha(0.5f)
        else -> null
    }

    bgColor?.let {
        modifier.background(RoundRectBackground(it, sizes.smallGap))
    }

    modifier
        .align(AlignmentX.Center, AlignmentY.Center)
        .margin(margin)
        .padding(padding)
        .onPointer { isClickFeedback = it.pointer.isLeftButtonDown }
        .onEnter { isHovered = true }
        .onExit {
            isHovered = false
            isClickFeedback = false
        }
        .onClick(onClick)

    Image {
        modifier
            .align(AlignmentX.Center, AlignmentY.Center)
            .iconImage(icon, 30.dp, tint)
    }

    tooltip?.let {
        Tooltip(it.lang, borderColor = colors.secondaryVariant)
    }

    boxBlock?.invoke(this)
}