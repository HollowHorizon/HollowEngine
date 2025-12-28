package ru.hollowhorizon.hollowengine.client.gui.scripting.files

import de.fabmax.kool.input.KeyboardInput
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
import ru.hollowhorizon.hollowengine.generated.Assets
import ru.hollowhorizon.hollowengine.mixins.kool.UiDockableAccessor

fun UiScope.LazyList(
    width: Dimension = Grow.Std,
    height: Dimension = Grow.Std,
    listOrientation: ListOrientation = ListOrientation.Horizontal,
    withVerticalScrollbar: Boolean = true,
    withHorizontalScrollbar: Boolean = false,
    isScrollableVertical: Boolean = true,
    isScrollableHorizontal: Boolean = true,
    scrollbarColor: Color? = null,
    containerModifier: ((UiModifier) -> Unit)? = null,
    scrollPaneModifier: ((ScrollPaneModifier) -> Unit)? = null,
    vScrollbarModifier: ((ScrollbarModifier) -> Unit)? = null,
    hScrollbarModifier: ((ScrollbarModifier) -> Unit)? = null,
    isScrollByDrag: Boolean = true,
    state: LazyListState = rememberListState(),
    scopeName: String? = null,
    block: LazyListScope.() -> Unit,
) {

    Box {
        modifier
            .width(width)
            .height(height)
            .backgroundColor(colors.backgroundVariant)
            .onWheelX {
                if (isScrollableHorizontal) {
                    state.scrollDpX(it.pointer.scroll.x * -20f)
                }
            }
            .onWheelY {
                if (isScrollableVertical && !KeyboardInput.isShiftDown) {
                    state.scrollDpY(it.pointer.scroll.y * -50f)
                }
                if (isScrollableHorizontal && KeyboardInput.isShiftDown) {
                    state.scrollDpX(it.pointer.scroll.y * -20f)
                }
            }

        if (isScrollByDrag) {
            modifier.onDrag {
                val delta = it.pointer.delta
                if (isScrollableHorizontal && delta.x != 0f) {
                    state.scrollDpX(Dp.fromPx(-delta.x).value)
                }
                if (isScrollableVertical && delta.y != 0f) {
                    state.scrollDpY(Dp.fromPx(-delta.y).value)
                }
            }
        }

        containerModifier?.invoke(modifier)

        ScrollPane(state) {
            // expand / grow list in cross axis direction
            val isGrowWidth = listOrientation == ListOrientation.Vertical
            val isGrowHeight = listOrientation == ListOrientation.Horizontal

            if (isGrowWidth) modifier.width(Grow.Std)
            if (isGrowHeight) modifier.height(Grow.Std)
            scrollPaneModifier?.let { it(modifier) }

            val lazyList = uiNode.createChild(scopeName, LazyListNode::class, LazyListNode.factory)
            lazyList.state = state
            lazyList.modifier
                .orientation(listOrientation)
                .layout(if (listOrientation == ListOrientation.Vertical) ColumnLayout else RowLayout)
            if (isGrowWidth) lazyList.modifier.width(Grow.Std)
            if (isGrowHeight) lazyList.modifier.height(Grow.Std)
            lazyList.block()
        }

        if (withVerticalScrollbar) {
            VerticalScrollbar {
                lazyListAware(state, ScrollbarOrientation.Vertical, listOrientation, scrollbarColor, vScrollbarModifier)
            }
        }
        if (withHorizontalScrollbar) {
            HorizontalScrollbar {
                lazyListAware(
                    state,
                    ScrollbarOrientation.Horizontal,
                    listOrientation,
                    scrollbarColor,
                    hScrollbarModifier
                )
            }
        }
    }
}

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
            LazyList(
                height = FitContent,
                isScrollByDrag = true,
                withHorizontalScrollbar = true,
                containerModifier = {
                    it.background(null).margin(bottom = 0.dp)

                },
                hScrollbarModifier = {
                    it.height(sizes.smallGap).margin(top = sizes.gap * 3f, bottom = sizes.borderWidth)
                }) {
                items(dockNode.dockedItems.filter { !it.isHidden }) { item ->
                    Row {
                        val isHovered by modifier.hoverable()
                        val factor by animateFloatAsState(if (isHovered) 1f else 0f, tween(easing = Easing.quadRev))

                        modifier
                            .margin(horizontal = sizes.smallGap)
                            .alignY(AlignmentY.Top)
                            .onClick {
                                if (it.pointer.isMiddleButtonReleased) {
                                    onCloseAction?.invoke(item)
                                } else if (it.isLeftClick) {
                                    dockNode.bringToTop(item)
                                } else if (it.isRightClick) {
                                    onRightClick(item, it)
                                }
                            }

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
                                modifier.onDragStart {
                                    if (getResizeEdgeMask(it) != 0) {
                                        // do not initiate move drag when pointer is on an edge, instead the drag will resize the dockItem
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
                        if (onCloseAction != null) {
                            CloseButton(
                                background = bgColor,
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
    icon: ResourceLocation,
    windowDockable: UiDockable,
    minimizeButton: MutableStateValue<Boolean>,
    isDraggable: Boolean = true,
    showTabsIfDocked: Boolean = true,
    onCloseAction: ((Dockable) -> Unit)? = null,
    onRightClick: (Dockable, PointerEvent) -> Unit = { dockable, event -> },
) {
    val isTabbed = if (showTabsIfDocked) {
        val hasAnyTabs: Boolean
        Column(Grow.Std) {
            hasAnyTabs = FileDockingTabsBar(windowDockable, onCloseAction = onCloseAction, onRightClick = onRightClick)
        }
        hasAnyTabs
    } else {
        false
    }

    if (!isTabbed) {
        if (windowDockable.floatingWidth.value == FitContent || windowDockable.floatingHeight.value == FitContent) {
            windowDockable.setFloatingBounds(450.dp, 200.dp)
        }
        FileDockingBar(icon, windowDockable, onCloseAction, onRightClick, minimizeButton, isDraggable)
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
    minimizeButton: MutableStateValue<Boolean>,
    isDraggable: Boolean,
) {
    Row(if(minimizeButton.use()) FitContent else Grow.Std) {
        if (windowDockable.isDocked.use()) modifier.margin(horizontal=Dimensions.PaddingMedium)
            .margin(top=Dimensions.PaddingMedium)
        modifier
            .onClick {
                if (it.pointer.isMiddleButtonReleased) {
                    onCloseAction?.invoke(windowDockable)
                } else if (it.isRightClick) {
                    onRightClick(windowDockable, it)
                }
            }

        Row(Grow.Std) {

            modifier.background(RoundRectBackground(ColorTheme.UI.BackgroundSecondary, Dimensions.PaddingNormal))
                .padding(
                    vertical = Dimensions.PaddingMedium,
                    horizontal = Dimensions.PaddingMedium + Dimensions.PaddingNormal
                )

            if (isDraggable && !PointerInput.primaryPointer.isMiddleButtonDown && !PointerInput.primaryPointer.isRightButtonDown) {
                with(windowDockable) {
                    registerDragCallbacks()
                }
            }

            Image(icon) {
                modifier.size(Dimensions.PaddingHuge, Dimensions.PaddingHuge)
                    .alignY(AlignmentY.Center)
            }

            val itemName =
                IdeContent.files.values.find { it.dockable == windowDockable }?.fileName ?: windowDockable.name.lang

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

            Box(Grow.Std) {}

            onCloseAction?.let { action ->
                Box {
                    modifier.size(Dimensions.PaddingHuge, Dimensions.PaddingHuge)
                        .background(
                            CloseButtonBackground(
                                ColorTheme.UI.WhiteReplacement,
                                Color.WHITE,
                                ColorTheme.UI.BackgroundSecondary,
                                ColorTheme.UI.BackgroundSecondary
                            )
                        )
                        .onClick {
                            if (it.isLeftClick) action(windowDockable)
                        }
                }
            }
        }
        Box(height = Grow.Std) {
            modifier.padding(Dimensions.PaddingMedium)
                .margin(start = Dimensions.PaddingNormal)

            val isHovered by modifier.hoverable()
            val color by animateColorAsState(if(isHovered) ColorTheme.UI.BackgroundAccent else ColorTheme.UI.BackgroundSecondary)
            modifier.background(RoundRectBackground(color, Dimensions.PaddingNormal))
                .onClick { if(it.pointer.isLeftButtonClicked) minimizeButton.set(!minimizeButton.use()) }

            Image(Assets.Hollowengine.Textures.Gui.Icons.MINIMIZE) {
                modifier.size(Dimensions.PaddingHuge, Dimensions.PaddingHuge)
                    .align(AlignmentX.Center, AlignmentY.Center)
            }
        }
    }
}

private fun UiDockable.moveUndockBoundsUnderPointer(itemBounds: MutableVec4f, ptrEv: PointerEvent) =
    (this as UiDockableAccessor).`hollowcore$moveUndockBoundsUnderPointer`(itemBounds, ptrEv)

private val UiDockable.dragStartItemBounds: MutableVec4f
    get() = (this as UiDockableAccessor).`hollowcore$getDragStartItemBounds`()

private val UiDockable.floatingWidthPx
    get() = (this as UiDockableAccessor).`hollowcore$getFloatingWidthPx`()
private val UiDockable.floatingHeightPx
    get() = (this as UiDockableAccessor).`hollowcore$getFloatingHeightPx`()

private fun UiNode.undockedBounds4f(floatingWidthPx: Float, floatingHeightPx: Float) =
    MutableVec4f(leftPx, topPx, leftPx + floatingWidthPx, topPx + floatingHeightPx)