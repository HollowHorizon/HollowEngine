package ru.hollowhorizon.hollowengine.client.gui.scripting.files

import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.modules.ui2.docking.UiDockable
import de.fabmax.kool.util.Color
import ru.hollowhorizon.hollowengine.client.gui.kool.backgroundMid
import ru.hollowhorizon.hollowengine.client.gui.kool.hoverBg


fun UiScope.FileDockingTabsBar(
    windowDockable: UiDockable,
    isDragToUndock: Boolean = true,
    onCloseAction: ((PointerEvent) -> Unit)? = null,
    scopeName: String? = null,
): Boolean {
    val dockNode = windowDockable.dockedTo.use()
    val nodeCount = dockNode?.dockedItems?.use()?.count { !it.isHidden } ?: 0

    if (dockNode != null && nodeCount > 1) {
        Row(width = Grow.Std, height = sizes.gap * 4f, scopeName = scopeName) {
            modifier.backgroundColor(colors.background)

            dockNode.dockedItems.filter { !it.isHidden }.forEach { item ->
                Box {
                    modifier
                        .margin(horizontal = sizes.smallGap)
                        .alignY(AlignmentY.Bottom)

                    Button(item.name) {
                        // set a bit different button style: click feedback is disabled (doesn't work with the way
                        // the tabs are switched)
                        // also we use a custom background to get a more "tabbie" look
                        val bgColor = if (isHovered) {
                            colors.hoverBg
                        } else {
                            colors.backgroundMid
                        }
                        modifier
                            .backgroundColor(bgColor)
                            .isClickFeedback(false)
                            .textAlignX(AlignmentX.Start)
                            .onClick {
                                if (it.pointer.isMiddleButtonReleased) {
                                    onCloseAction?.invoke(it)
                                } else if (it.isLeftClick) {
                                    dockNode.bringToTop(item)
                                }
                            }

                        if (onCloseAction != null) {
                            modifier
                                .text(modifier.text + "     ")
                                .padding(end = 0.dp)

                            CloseButton(
                                buttonMod = {
                                    it
                                        .align(AlignmentX.End, AlignmentY.Center)
                                        .margin(top = sizes.smallGap, end = sizes.smallGap)
                                        .width(sizes.gap * 3f)
                                        .height(sizes.gap * 3f)
                                }
                            ) { ev -> onCloseAction(ev) }
                        }
                    }

                    if (item == windowDockable) {
                        // active tab indicator
                        Box(Grow.Std, sizes.borderWidth * 2f) {
                            modifier
                                .backgroundColor(if (surface.isFocused.use()) colors.primary else colors.primaryVariant)
                                .alignY(AlignmentY.Bottom)
                        }
                        if (isDragToUndock) {
                            with(windowDockable) {
                                registerDragCallbacks(false)
                            }
                        }
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
        FileDockingTabsBar(windowDockable, onCloseAction = onCloseAction, scopeName = scopeName)
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
        Row(Grow.Std, height = sizes.gap * 4f, scopeName = scopeName) {
            val color = if (surface.isFocused.use()) focusedBackgroundColor else unfocusedBackgroundColor
            val cornerR = if (windowDockable.isDocked.use()) 0f else sizes.gap.px
            modifier
                .padding(horizontal = sizes.gap)
                .background(TitleBarBackground(color, cornerR, isMinimizedToTitle))

            if (isDraggable) {
                with(windowDockable) {
                    registerDragCallbacks()
                }
            }

            Text(title) {
                modifier
                    .width(Grow.Std)
                    .margin(horizontal = sizes.gap, vertical = sizes.smallGap * 0.5f)
                    .textColor(if (surface.isFocused.use()) focusedTextColor else unfocusedTextColor)
                    .alignY(AlignmentY.Center)
            }

            onCloseAction?.let {
                CloseButton(buttonMod = {
                    it
                        .align(AlignmentX.End, AlignmentY.Center)
                        .margin(top = sizes.smallGap, end = sizes.smallGap)
                        .width(sizes.gap * 3f)
                        .height(sizes.gap * 3f)
                }) { ev -> it(ev) }
            }
        }
    } else {
        // add an empty row to avoid a hard layout change when the title bar changes visibility
        Row { }
    }
}