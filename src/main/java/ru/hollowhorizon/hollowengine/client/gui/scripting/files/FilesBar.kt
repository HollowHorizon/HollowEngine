package ru.hollowhorizon.hollowengine.client.gui.scripting.files

import de.fabmax.kool.input.PointerInput
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.modules.ui2.docking.UiDockable
import de.fabmax.kool.util.Color
import ru.hollowhorizon.hollowengine.client.gui.kool.backgroundMid
import ru.hollowhorizon.hollowengine.client.gui.kool.hoverBg
import ru.hollowhorizon.hollowengine.client.gui.scripting.IdeContent
import ru.hollowhorizon.hollowengine.client.gui.scripting.tools.TabRenderer
import ru.hollowhorizon.hollowengine.client.gui.scripting.tools.hoverListener
import ru.hollowhorizon.hollowengine.client.utils.lang


fun UiScope.FileDockingTabsBar(
    windowDockable: UiDockable,
    isDragToUndock: Boolean = true,
    onCloseAction: ((PointerEvent) -> Unit)? = null
): Boolean {
    val dockNode = windowDockable.dockedTo.use()
    val nodeCount = dockNode?.dockedItems?.use()?.count { !it.isHidden } ?: 0

    if (dockNode != null && nodeCount > 1) {

        Row(width = Grow.Std) {
            modifier
                .background(RoundRectBackground(colors.background, sizes.smallGap))

            dockNode.dockedItems.filter { !it.isHidden }.forEach { item ->
                Row {
                    val isHovered by hoverListener()

                    modifier
                        .margin(sizes.smallGap)
                        .padding(sizes.smallGap)
                        .alignY(AlignmentY.Bottom)
                        .background(RoundRectBackground(colors.background, sizes.smallGap))
                        .border(RoundRectBorder(Color("3C3C4AFF"), sizes.smallGap, sizes.borderWidth))
                        .onClick {
                            if (it.pointer.isMiddleButtonReleased) {
                                onCloseAction?.invoke(it)
                            } else if (it.isLeftClick) {
                                dockNode.bringToTop(item)
                            }
                        }

                    if(isHovered) {
                        modifier
                            .background(RoundRectBackground(colors.background.mulRgb(1.5f), sizes.smallGap))
                            .border(RoundRectBorder(Color("3C3C4AFF").mulRgb(1.5f), sizes.smallGap, sizes.borderWidth))
                    }

                    val itemName = IdeContent.files.values.find { it.dockable == item }?.fileName ?: item.name.lang

                    Text(itemName) {
                        modifier.textAlign(AlignmentX.Start, AlignmentY.Center)
                            .font(sizes.normalText)
                    }

                    if (isDragToUndock) {
                        with(windowDockable) {
                            registerDragCallbacks(false)
                        }
                    }
                    if (onCloseAction != null) {
                        CloseButton(
                            buttonMod = {
                                it.align(AlignmentX.End, AlignmentY.Center)
                                    .margin(end = sizes.smallGap)
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
    isDraggable: Boolean = true,
    showTabsIfDocked: Boolean = true,
    onCloseAction: ((PointerEvent) -> Unit)? = null
) {
    val isTabbed = if (showTabsIfDocked) {
        FileDockingTabsBar(windowDockable, onCloseAction = onCloseAction)
    } else {
        false
    }

    if (!isTabbed) {
        if (windowDockable.floatingWidth.value == FitContent || windowDockable.floatingHeight.value == FitContent) {
            windowDockable.setFloatingBounds(
                width = 450.dp,
                height = 200.dp
            )
        }
        Row(Grow.Std) {
            modifier
                .background(RoundRectBackground(colors.background, sizes.smallGap))
                .border(RoundRectBorder(Color("3C3C4AFF"), sizes.smallGap, sizes.borderWidth))
                .margin(sizes.smallGap, sizes.smallGap, 0.dp, sizes.smallGap)
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
                IdeContent.files.values.find { it.dockable == windowDockable }?.fileName ?: windowDockable.name.lang

            Text(itemName) {
                modifier
                    .width(Grow.Std)
                    .margin(horizontal = sizes.gap, vertical = sizes.smallGap * 0.5f)
                    .align(AlignmentX.Center, AlignmentY.Center)
                    .textAlign(AlignmentX.Center, AlignmentY.Center)
            }

            onCloseAction?.let {
                CloseButton(buttonMod = {
                    it
                        .align(AlignmentX.End, AlignmentY.Center)
                        .padding(sizes.smallGap)
                }) { ev -> it(ev) }
            }
        }
    } else {
        // add an empty row to avoid a hard layout change when the title bar changes visibility
        Row { }
    }
}