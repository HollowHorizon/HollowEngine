package ru.hollowhorizon.hollowengine.client.gui.scripting.files

import de.fabmax.kool.input.CursorShape
import de.fabmax.kool.input.PointerInput
import de.fabmax.kool.math.Easing
import de.fabmax.kool.math.MutableVec4f
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.modules.ui2.docking.Dockable
import de.fabmax.kool.modules.ui2.docking.UiDockable
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.MsdfFont
import net.minecraft.resources.ResourceLocation
import ru.hollowhorizon.hollowengine.client.gui.colors.ColorTheme
import ru.hollowhorizon.hollowengine.client.gui.colors.Dimensions
import ru.hollowhorizon.hollowengine.client.gui.scripting.IdeContent
import ru.hollowhorizon.hollowengine.client.gui.scripting.tools.hoverable
import ru.hollowhorizon.hollowengine.client.kool.minecraft.Image
import ru.hollowhorizon.hollowengine.client.utils.lang
import ru.hollowhorizon.hollowengine.common.codeblocks.modules.icons
import ru.hollowhorizon.hollowengine.runtime.transform.kool.UiDockableAccessor

fun UiScope.FileDockingTabsBar(
    windowDockable: UiDockable,
    isDragToUndock: Boolean = true,
    onCloseAction: ((Dockable) -> Unit)? = null,
    onRightClick: (Dockable, PointerEvent) -> Unit = { dockable, event -> },
): Boolean {
    val dockNode = windowDockable.dockedTo.use()
    val nodeCount = dockNode?.dockedItems?.use()?.count { !it.isHidden } ?: 0

    if (dockNode != null && nodeCount > 1) {

        Row(width = Grow.Std) {
            modifier.margin(vertical = sizes.smallGap)
            val tabsScrollState = rememberScrollState()
            val visibleDockables = dockNode.dockedItems.filter { !it.isHidden }
            val activeDockable = dockNode.dockItemOnTop
            ScrollArea(
                height = FitContent,
                withVerticalScrollbar = false,
                withHorizontalScrollbar = true,
                isScrollableVertical = false,
                state = tabsScrollState,
                containerModifier = { it.background(null) },
                hScrollbarModifier = {
                    it.height(sizes.smallGap).margin(top = sizes.gap * 3f, bottom = sizes.borderWidth)
                }) {
                Row(height = Grow.Std) {
                    visibleDockables.forEach { item ->
                        FileDockingTab(
                            item = item,
                            isDragToUndock = isDragToUndock,
                            isActive = item == activeDockable,
                            onActivate = {
                                dockNode.bringToTop(item)
                                surface.triggerUpdate()
                            },
                            onCloseAction = onCloseAction,
                            onRightClick = onRightClick,
                        )
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

private fun UiScope.FileDockingTab(
    item: Dockable,
    isDragToUndock: Boolean,
    isActive: Boolean,
    onActivate: () -> Unit,
    onCloseAction: ((Dockable) -> Unit)?,
    onRightClick: (Dockable, PointerEvent) -> Unit,
) {
    Row {
        val file = IdeContent.files.values.find { it.dockable == item }
        val icon = file?.icon ?: icons.GENERAL
        val itemName = file?.filePath?.substringAfterLast('/') ?: item.name.lang
        val isHovered by modifier.hoverable()
        val factor by animateFloatAsState(
            if (isHovered || isActive) 1f else 0f,
            tween(0.16f, Easing.easeOutQuart),
        )
        val bgColor = ColorTheme.UI.BackgroundSecondary.mix(ColorTheme.UI.BackgroundElements, factor)
        val borderColor = ColorTheme.UI.BackgroundElements.mix(ColorTheme.UI.BackgroundAccent, if (isActive) 1f else factor)

        modifier
            .margin(horizontal = Dimensions.PaddingSmall)
            .alignY(AlignmentY.Top)
            .padding(horizontal = Dimensions.PaddingNormal, vertical = Dimensions.PaddingSmall)
            .height(FitContent)
            .background(RoundRectBackground(bgColor, Dimensions.PaddingNormal))
            .border(RoundRectBorder(borderColor, Dimensions.PaddingNormal, sizes.borderWidth))
            .onClick {
                if (it.pointer.isMiddleButtonReleased) {
                    closeDockable(item, onCloseAction)
                } else if (it.isLeftClick) {
                    onActivate()
                } else if (it.isRightClick) {
                    onRightClick(item, it)
                }
            }

        val tabDockable = item as? UiDockable
        if (isDragToUndock && tabDockable != null) {
            with(tabDockable) {
                modifier.onDragStart {
                    if (getResizeEdgeMask(it) != 0) {
                        it.isConsumed = false
                    }
                }

                var moved by remember(false)

                modifier.onDrag {
                    if (it.pointer.pos.y in uiNode.topPx..uiNode.bottomPx) {
                        return@onDrag
                    }

                    if (!moved) {
                        moved = true
                        dockedTo.value?.undock(this)
                        val itemBounds = uiNode.undockedBounds4f(floatingWidthPx, floatingHeightPx)
                        moveUndockBoundsUnderPointer(itemBounds, it)
                        dragStartItemBounds.set(itemBounds)
                        floatingX.set(Dp.fromPx(it.pointer.pos.x))
                        floatingY.set(Dp.fromPx(it.pointer.pos.y))
                        dock?.dndContext?.startDrag(this, it, null)
                        return@onDrag
                    }

                    floatingX.set(floatingX.value + Dp.fromPx(it.pointer.delta.x))
                    floatingY.set(floatingY.value + Dp.fromPx(it.pointer.delta.y))
                    floatingAlignmentX.set(AlignmentX.Start)
                    floatingAlignmentY.set(AlignmentY.Top)
                    dock?.dndContext?.drag(it)
                }
                modifier.onDragEnd {
                    dock?.dndContext?.endDrag(it)
                    moved = false
                }
                return@with
                registerDragCallbacks(false)
            }
        }

        Image(icon) {
            modifier
                .size(Dimensions.PaddingLarge, Dimensions.PaddingLarge)
                .alignY(AlignmentY.Center)
        }

        Text(itemName) {
            modifier
                .margin(horizontal = Dimensions.PaddingMedium)
                .font(remember {
                    MsdfFont(
                        ColorTheme.Fonts.MONOCRAFT,
                        Dimensions.FontNormal,
                        MsdfFont.ITALIC_NONE,
                        MsdfFont.WEIGHT_EXTRA_BOLD,
                    )
                })
                .textColor(ColorTheme.UI.WhiteReplacement)
                .align(AlignmentX.Start, AlignmentY.Center)
        }

        onCloseAction?.let { action ->
            TabCloseButton(icons.CLOSE) {
                closeDockable(item, action)
            }
        }
    }
}

private fun closeDockable(item: Dockable, fallback: ((Dockable) -> Unit)?) {
    val file = IdeContent.files.values.firstOrNull { it.dockable == item }
    if (file != null) {
        file.close()
    } else {
        fallback?.invoke(item)
    }
}

fun UiScope.FileTitleBar(
    icon: ResourceLocation,
    windowDockable: UiDockable,
    isDraggable: Boolean = true,
    onCloseAction: ((Dockable) -> Unit)? = null,
    onRightClick: (Dockable, PointerEvent) -> Unit = { dockable, event -> },
    headerLeft: UiScope.(background: Color) -> Unit = {},
    headerRight: UiScope.(background: Color) -> Unit = {},
) {
    val isTabbed = run {
        val hasAnyTabs: Boolean
        Column(Grow.Std) {
            hasAnyTabs = FileDockingTabsBar(windowDockable, onCloseAction = onCloseAction, onRightClick = onRightClick)
        }
        hasAnyTabs
    }

    if (!isTabbed) {
        if (windowDockable.floatingWidth.value == FitContent || windowDockable.floatingHeight.value == FitContent) {
            windowDockable.setFloatingBounds(450.dp, 200.dp)
        }
        FileDockingBar(
            icon = icon,
            windowDockable = windowDockable,
            onCloseAction = onCloseAction,
            onRightClick = onRightClick,
            isDraggable = isDraggable,
            headerLeft = headerLeft,
            headerRight = headerRight,
        )
    } else {
        // add an empty row to avoid a hard layout change when the title bar changes visibility
        Row { }
    }
}

private fun UiScope.FileDockingBar(
    icon: ResourceLocation,
    windowDockable: UiDockable,
    onCloseAction: ((Dockable) -> Unit)?,
    onRightClick: (Dockable, PointerEvent) -> Unit,
    isDraggable: Boolean,
    headerLeft: UiScope.(background: Color) -> Unit,
    headerRight: UiScope.(background: Color) -> Unit,
) {
    Row(Grow.Std) {
        val isFocused = surface.isFocused.use()
        if (windowDockable.isDocked.use()) modifier.margin(Dimensions.PaddingNormal)
        modifier
            .onClick {
                if (it.pointer.isMiddleButtonReleased) {
                    onCloseAction?.invoke(windowDockable)
                } else if (it.pointer.isRightButtonClicked) {
                    onRightClick(windowDockable, it)
                }
            }
            .onHover {
                PointerInput.cursorShape = CursorShape.HAND
                it.isConsumed = false
            }
            .onDrag { PointerInput.cursorShape = CursorShape.HAND }

        Box(Grow.Std) {
            val isHovered by modifier.hoverable()
            val factor by animateFloatAsState(
                if (isHovered || isFocused) 1f else 0f,
                tween(0.16f, Easing.easeOutQuart),
            )
            val color by animateColorAsState(
                ColorTheme.UI.BackgroundSecondary.mix(ColorTheme.UI.BackgroundElements, factor),
                tween(0.16f, Easing.easeOutQuart),
            )
            val borderColor = ColorTheme.UI.BackgroundElements.mix(ColorTheme.UI.BackgroundAccent, if (isFocused) 1f else factor)

            modifier
                .padding(Dimensions.PaddingNormal)
                .height(FitContent)
                .background(RoundRectBackground(color, Dimensions.PaddingNormal))
                .border(RoundRectBorder(borderColor, Dimensions.PaddingNormal, sizes.borderWidth))


            if (isDraggable && !PointerInput.primaryPointer.isMiddleButtonDown && !PointerInput.primaryPointer.isRightButtonDown) {
                with(windowDockable) {
                    registerDragCallbacks()
                }
            }

            Row {
                Image(icon) {
                    modifier.size(Dimensions.PaddingLarge, Dimensions.PaddingLarge)
                        .alignY(AlignmentY.Center)
                }

                val itemName =
                    IdeContent.files.values.find { it.dockable == windowDockable }?.filePath?.substringAfterLast('/')
                        ?: windowDockable.name.lang

                Text(itemName) {
                    modifier
                        .margin(horizontal = Dimensions.PaddingMedium)
                        .font(remember {
                            MsdfFont(
                                ColorTheme.Fonts.MONOCRAFT,
                                Dimensions.FontNormal,
                                MsdfFont.ITALIC_NONE,
                                MsdfFont.WEIGHT_EXTRA_BOLD
                            )
                        })
                        .textColor(ColorTheme.UI.WhiteReplacement)
                        .align(AlignmentX.Start, AlignmentY.Center)
                }

                headerLeft(color)
            }


            Row {
                modifier.align(AlignmentX.End, AlignmentY.Center)

                headerRight(color)

                onCloseAction?.let { action ->
                    HeaderIconButton(icons.CLOSE) {
                        action(windowDockable)
                    }
                }
            }
        }
    }
}

private fun UiScope.HeaderIconButton(icon: ResourceLocation, onClick: () -> Unit) {
    Box {
        val isHovered by modifier.hoverable()
        val color by animateColorAsState(
            if (isHovered) ColorTheme.UI.BackgroundAccent else ColorTheme.UI.BackgroundElements,
            tween(0.12f, Easing.easeOutQuart),
        )
        modifier
            .alignY(AlignmentY.Center)
            .padding(Dimensions.PaddingNormal)
            .background(RoundRectBackground(color, Dimensions.PaddingNormal))
            .onClick {
                if (it.isLeftClick) onClick()
            }

        Image(icon) {
            modifier
                .size(Dimensions.PaddingHuge, Dimensions.PaddingHuge)
                .align(AlignmentX.Center, AlignmentY.Center)
        }
    }
}

private fun UiScope.TabCloseButton(icon: ResourceLocation, onClick: () -> Unit) {
    Box {
        val isHovered by modifier.hoverable()
        val color by animateColorAsState(
            if (isHovered) ColorTheme.UI.BackgroundAccent else ColorTheme.UI.BackgroundElements,
            tween(0.12f, Easing.easeOutQuart),
        )
        modifier
            .alignY(AlignmentY.Center)
            .padding(Dimensions.PaddingSmall)
            .background(RoundRectBackground(color, Dimensions.PaddingSmall))
            .onClick {
                if (it.isLeftClick) onClick()
            }

        Image(icon) {
            modifier
                .size(Dimensions.PaddingLarge, Dimensions.PaddingLarge)
                .align(AlignmentX.Center, AlignmentY.Center)
        }
    }
}

@Suppress("CAST_NEVER_SUCCEEDS")
private fun UiDockable.moveUndockBoundsUnderPointer(itemBounds: MutableVec4f, ptrEv: PointerEvent) =
    (this as UiDockableAccessor).`hollowcore$moveUndockBoundsUnderPointer`(itemBounds, ptrEv)

@Suppress("CAST_NEVER_SUCCEEDS")
private val UiDockable.dragStartItemBounds: MutableVec4f
    get() = (this as UiDockableAccessor).`hollowcore$getDragStartItemBounds`()

@Suppress("CAST_NEVER_SUCCEEDS")
private val UiDockable.floatingWidthPx
    get() = (this as UiDockableAccessor).`hollowcore$getFloatingWidthPx`()

@Suppress("CAST_NEVER_SUCCEEDS")
private val UiDockable.floatingHeightPx
    get() = (this as UiDockableAccessor).`hollowcore$getFloatingHeightPx`()

private fun UiNode.undockedBounds4f(floatingWidthPx: Float, floatingHeightPx: Float) =
    MutableVec4f(leftPx, topPx, leftPx + floatingWidthPx, topPx + floatingHeightPx)
