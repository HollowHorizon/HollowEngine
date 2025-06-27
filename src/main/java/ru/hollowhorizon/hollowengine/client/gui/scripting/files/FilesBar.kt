package ru.hollowhorizon.hollowengine.client.gui.scripting.files

import de.fabmax.kool.input.PointerInput
import de.fabmax.kool.math.Easing
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.modules.ui2.docking.Dockable
import de.fabmax.kool.modules.ui2.docking.UiDockable
import de.fabmax.kool.util.Color
import ru.hollowhorizon.hollowengine.client.gui.scripting.IdeContent
import ru.hollowhorizon.hollowengine.client.gui.scripting.tools.hoverListener
import ru.hollowhorizon.hollowengine.client.utils.lang


fun UiScope.FileDockingTabsBar(
    windowDockable: UiDockable,
    isDragToUndock: Boolean = true,
    onCloseAction: ((Dockable) -> Unit)? = null,
    onRightClick: (Dockable, PointerEvent) -> Unit = { dockable, event -> }
): Boolean {
    val dockNode = windowDockable.dockedTo.use()
    val nodeCount = dockNode?.dockedItems?.use()?.count { !it.isHidden } ?: 0

    if (dockNode != null && nodeCount > 1) {

        Row(width = Grow.Std) {

            LazyRow(height = FitContent, containerModifier = { it.background(null) }) {
                items(dockNode.dockedItems.filter { !it.isHidden }) { item ->
                    Row {
                        val (isHovered, anim) = hoverListener { !dockNode.isOnTop(item) }

                        modifier
                            .margin(horizontal = sizes.smallGap)
                            .alignY(AlignmentY.Bottom)
                            .onClick {
                                if (it.pointer.isMiddleButtonReleased) {
                                    onCloseAction?.invoke(item)
                                } else if (it.isLeftClick) {
                                    dockNode.bringToTop(item)
                                } else if(it.isRightClick) {
                                    onRightClick(item, it)
                                }
                            }

                        var factor = Easing.quadRev(anim.progressAndUse())
                        if (!isHovered.use() && !dockNode.isOnTop(item)) factor = 1f - factor
                        val bgColor = colors.background.mix(Color("394450FF"), factor)
                        val borderColor = Color("3C3C4AFF").mix(Color("586D84FF"), factor)

                        modifier
                            .background(RoundRectBackground(bgColor, sizes.smallGap))
                            .border(RoundRectBorder(borderColor, sizes.smallGap, sizes.borderWidth))


                        val itemName = IdeContent.files.values.find { it.dockable == item }?.fileName ?: item.name.lang

                        Text(itemName) {
                            modifier.textAlign(AlignmentX.Start, AlignmentY.Center)
                                .margin(horizontal = sizes.gap, vertical = sizes.smallGap * 0.5f)
                                .font(sizes.normalText)
                        }

                        if (isDragToUndock) {
                            with(windowDockable) {
                                registerDragCallbacks(false)
                            }
                        }
                        if (onCloseAction != null) {
                            CloseButton(background = bgColor,
                                backgroundHover = bgColor,
                                foreground = colors.onBackground,
                                buttonMod = {
                                    it.onDragStart {}.onDragEnd {}.onDrag {} // Deny drag by close button
                                        .align(AlignmentX.End, AlignmentY.Center)
                                        .margin(end = sizes.smallGap)
                                }
                            ) { ev -> onCloseAction(item) }
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
    isDraggable: Boolean = true,
    showTabsIfDocked: Boolean = true,
    onCloseAction: ((Dockable) -> Unit)? = null,
    onRightClick: (Dockable, PointerEvent) -> Unit = { dockable, event ->}
) {
    val isTabbed = if (showTabsIfDocked) {
        FileDockingTabsBar(windowDockable, onCloseAction = onCloseAction, onRightClick = onRightClick)
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
            val (isHovered, anim) = hoverListener { !surface.isFocused.use() }

            modifier
                .margin(sizes.smallGap, sizes.smallGap, 0.dp, sizes.smallGap)
                .onClick {
                    if (it.pointer.isMiddleButtonReleased) {
                        onCloseAction?.invoke(windowDockable)
                    } else if(it.isRightClick) {
                        onRightClick(windowDockable, it)
                    }
                }

            var factor = Easing.quadRev(anim.progressAndUse())
            if (!isHovered.use() && !surface.isFocused.use()) factor = 1f - factor
            val bgColor = colors.background.mix(Color("394450FF"), factor)
            val borderColor = Color("3C3C4AFF").mix(Color("586D84FF"), factor)

            modifier
                .background(RoundRectBackground(bgColor, sizes.smallGap))
                .border(RoundRectBorder(borderColor, sizes.smallGap, sizes.borderWidth))

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
                CloseButton(
                    background = bgColor,
                    backgroundHover = bgColor,
                    foreground = colors.onBackground,
                    buttonMod = {
                        it
                            .onDragStart {}.onDragEnd {}.onDrag {} // Deny drag by close button
                            .align(AlignmentX.End, AlignmentY.Center)
                            .padding(sizes.smallGap)
                    }) { ev -> it(windowDockable) }
            }
        }
    } else {
        // add an empty row to avoid a hard layout change when the title bar changes visibility
        Row { }
    }
}