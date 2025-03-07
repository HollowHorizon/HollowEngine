package ru.hollowhorizon.hollowengine.client.gui.scripting.files

import de.fabmax.kool.input.PointerInput
import de.fabmax.kool.math.MutableVec4f
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.modules.ui2.docking.UiDockable
import de.fabmax.kool.util.Color
import ru.hollowhorizon.hollowengine.client.gui.kool.backgroundMid
import ru.hollowhorizon.hollowengine.client.gui.kool.hoverBg
import ru.hollowhorizon.hollowengine.client.gui.scripting.IDEGuiV2
import ru.hollowhorizon.hollowengine.client.gui.scripting.tools.TabRenderer
import ru.hollowhorizon.hollowengine.client.gui.scripting.tools.hoverListener
import ru.hollowhorizon.hollowengine.client.utils.lang


fun UiScope.FileDockingTabsBar(
    windowDockable: UiDockable,
    isDragToUndock: Boolean = true,
    onCloseAction: ((PointerEvent) -> Unit)? = null,
    scopeName: String? = null,
    focusedBackgroundColor: Color = colors.secondary,
    unfocusedBackgroundColor: Color = colors.secondaryVariant,
): Boolean {
    val dockNode = windowDockable.dockedTo.use()
    val nodeCount = dockNode?.dockedItems?.use()?.count { !it.isHidden } ?: 0

    if (dockNode != null && nodeCount > 1) {
        val color = if (surface.isFocused.use()) focusedBackgroundColor else unfocusedBackgroundColor

        Row(width = Grow.Std, height = 12.dp, scopeName = scopeName) {
            modifier.background(
                RoundRectGradientBackground(
                    sizes.smallGap, color.mulRgb(0.5f), color,
                    0.dp, 5.dp, 100.dp, 100.dp
                )
            )

            dockNode.dockedItems.filter { !it.isHidden }.forEach { item ->
                Row {
                    val isHovered by hoverListener()
                    val bgColor = if (isHovered) colors.hoverBg
                    else colors.backgroundMid

                    modifier
                        .margin(horizontal = sizes.smallGap*0.5f)
                        .padding(sizes.smallGap * 0.5f)
                        .alignY(AlignmentY.Bottom)
                        .background(
                            TabRenderer(
                                bgColor,
                                when {
                                    item == windowDockable -> colors.primaryVariant
                                    isHovered -> colors.primary
                                    else -> bgColor
                                }
                            )
                        )
                        .onClick {
                            if (it.pointer.isMiddleButtonReleased) {
                                onCloseAction?.invoke(it)
                            } else if (it.isLeftClick) {
                                dockNode.bringToTop(item)
                            }
                        }

                    val itemName = IDEGuiV2.files.values.find { it.dockable == item }?.fileName ?: item.name.lang

                    Text(itemName) {
                        modifier.textAlign(AlignmentX.Start, AlignmentY.Center)
                            .font(sizes.normalText.derive(8f))
                            .height(10.dp)
                    }

                    if (isDragToUndock) {
                        with(windowDockable) {
                            registerDragCallbacks(false)
                        }
                    }
                    if (onCloseAction != null) {
                        CloseButton(
                            buttonMod = {
                                it
                                    .margin(0f.dp)
                                    .align(AlignmentX.End, AlignmentY.Center)
                                    .size(sizes.gap, sizes.gap)
                            }
                        ) { ev -> onCloseAction(ev) }
                    }
                }
            }
        }
        return true
    } else {
        // add an empty row to avoid a hard layout change when the tab row changes visibility
        Row { }
        return false
    }
}

fun UiScope.FileTitleBar(
    windowDockable: UiDockable,
    title: String = windowDockable.name,
    focusedBackgroundColor: Color = colors.secondary,
    unfocusedBackgroundColor: Color = colors.secondaryVariant,
    focusedTextColor: Color = colors.onSecondary,
    unfocusedTextColor: Color = colors.onSecondary,
    isDraggable: Boolean = true,
    isMinimizedToTitle: Boolean = false,
    showTabsIfDocked: Boolean = true,
    hideTitleWhenTabbed: Boolean = true,
    onCloseAction: ((PointerEvent) -> Unit)? = null,
    scopeName: String? = null,
) {
    val isTabbed = if (showTabsIfDocked) {
        FileDockingTabsBar(windowDockable, onCloseAction = onCloseAction, scopeName = scopeName,
            focusedBackgroundColor = focusedBackgroundColor, unfocusedBackgroundColor = unfocusedBackgroundColor)
    } else {
        false
    }

    if (!isTabbed || !hideTitleWhenTabbed) {
        if (windowDockable.floatingWidth.value == FitContent || windowDockable.floatingHeight.value == FitContent) {
            windowDockable.setFloatingBounds(
                width = 450.dp,
                height = 200.dp
            )
        }
        Row(Grow.Std, height = 12.dp, scopeName = scopeName) {
            val color = if (surface.isFocused.use()) focusedBackgroundColor else unfocusedBackgroundColor
            val cornerR = if (windowDockable.isDocked.use()) 0f else sizes.smallGap.px * 0.5f
            modifier
                .padding(horizontal = sizes.gap)
                .background(
                    RoundRectGradientBackground(
                        cornerR.dp, color.mulRgb(0.5f), color,
                        0.dp, 5.dp, 100.dp, 100.dp
                    )
                )
                .onClick {
                    if (it.pointer.isMiddleButtonReleased) {
                        onCloseAction?.invoke(it)
                    }
                }
            if (isDraggable && !PointerInput.primaryPointer.isMiddleButtonDown && !PointerInput.primaryPointer.isRightButtonDown) {
                with(windowDockable) {
                    registerDragCallbacks()
                }
            }

            val itemName =
                IDEGuiV2.files.values.find { it.dockable == windowDockable }?.fileName ?: windowDockable.name.lang

            Text(itemName) {
                modifier
                    .width(Grow.Std)
                    .margin(horizontal = sizes.gap, vertical = sizes.smallGap * 0.5f)
                    .textColor(if (surface.isFocused.use()) focusedTextColor else unfocusedTextColor)
                    .align(AlignmentX.Center, AlignmentY.Center)
                    .textAlign(AlignmentX.Center, AlignmentY.Center)
            }

            onCloseAction?.let {
                CloseButton(buttonMod = {
                    it
                        .align(AlignmentX.End, AlignmentY.Center)
                        .margin(top = sizes.smallGap, end = sizes.smallGap)
                        .width(sizes.gap)
                        .height(sizes.gap)
                }) { ev -> it(ev) }
            }
        }
    } else {
        // add an empty row to avoid a hard layout change when the title bar changes visibility
        Row { }
    }
}