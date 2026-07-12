package ru.hollowhorizon.hollowengine.client.ui.docking

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import org.lwjgl.glfw.GLFW
import ru.hollowhorizon.hollowengine.client.ui.*
import ru.hollowhorizon.hollowengine.client.ui.style.UiTextOverflow
import ru.hollowhorizon.hollowengine.client.utils.lang

private const val DockTabMinWidth = 72f
private const val DockTabMaxWidth = 180f
private const val DockTabHeight = 18f
private const val DockTabMargin = 2f
private const val DockTabCloseWidth = 22f

typealias DockItemContent = @Composable (DockItem) -> Unit
typealias DockHeaderContent = @Composable (DockItem) -> Unit

@Composable
fun DockSpace(
    state: DockingState,
    id: String = "dock-space",
    modifier: Modifier = Modifier.size(100.percent, 100.percent),
    tabContent: DockHeaderContent = { item -> DefaultDockTabContent(item) },
    headerContent: DockHeaderContent = { item -> DefaultDockHeaderContent(item) },
    content: DockItemContent,
) {
    Box(
        id = id,
        tags = listOf(DockTags.Space),
        modifier = modifier.style("hollowengine:ui/styles/docking.hss").clip(),
    ) {
        Box(
            id = "$id-root",
            mode = UiBoxMode.STACK,
            tags = listOf(DockTags.Root),
            modifier = Modifier.size(100.percent, 100.percent),
        ) {
            state.root?.let { root ->
                key(root.id) {
                    DockNodeView(root, state, tabContent, content)
                }
            }
        }

        state.floatingWindows.forEachIndexed { index, window ->
            key(window.id) {
                FloatingDockWindowView(window, state, index, tabContent, headerContent, content)
            }
        }

        if (state.draggedWindowId != null) {
            DockDropOverlay(state)
        }
    }
}

@Composable
private fun DockNodeView(
    node: DockNode,
    state: DockingState,
    tabContent: DockHeaderContent,
    content: DockItemContent,
) {
    when (node) {
        is DockNode.Stack -> DockStackView(node, state, tabContent, content)
        is DockNode.Split -> DockSplitView(node, state, tabContent, content)
    }
}

@Composable
private fun DockSplitView(
    split: DockNode.Split,
    state: DockingState,
    tabContent: DockHeaderContent,
    content: DockItemContent,
) {
    val horizontal = split.orientation == DockOrientation.HORIZONTAL
    val modifier = Modifier.size(100.percent, 100.percent)
    if (horizontal) {
        Row(id = split.id, tags = listOf(DockTags.Split), modifier = modifier) {
            SplitContent(split, state, tabContent, content, horizontal)
        }
    } else {
        Column(id = split.id, tags = listOf(DockTags.Split), modifier = modifier) {
            SplitContent(split, state, tabContent, content, horizontal)
        }
    }
}

@Composable
private fun SplitContent(
    split: DockNode.Split,
    state: DockingState,
    tabContent: DockHeaderContent,
    content: DockItemContent,
    horizontal: Boolean,
) {
    Box(modifier = splitPaneModifier(horizontal, split.fraction)) {
        DockNodeView(split.first, state, tabContent, content)
    }
    Splitter(split, state, horizontal)
    Box(modifier = splitPaneModifier(horizontal, 1f - split.fraction)) {
        DockNodeView(split.second, state, tabContent, content)
    }
}

@Composable
private fun Splitter(
    split: DockNode.Split,
    state: DockingState,
    horizontal: Boolean,
) {
    val size = 3.px
    Box(
        id = "${split.id}-splitter",
        tags = listOf(DockTags.Splitter),
        modifier = Modifier.size(if (horizontal) size else 100.percent, if (horizontal) 100.percent else size)
            .input(hoverable = true, draggable = true)
            .cursor(if (horizontal) UiCursorShape.RESIZE_HORIZONTAL else UiCursorShape.RESIZE_VERTICAL)
            .onDrag { event ->
                val parentSize = if (horizontal) event.parentWidth else event.parentHeight
                val splitterSize = size.value
                val paneSize = parentSize - splitterSize
                if (paneSize > 0f) {
                    val delta = if (horizontal) event.deltaX else event.deltaY
                    state.resizeSplitByFraction(split.id, delta / paneSize)
                }
                event.consume()
            }
    )
}

@Composable
private fun DockStackView(
    stack: DockNode.Stack,
    state: DockingState,
    tabContent: DockHeaderContent,
    content: DockItemContent,
) {
    Column(
        id = stack.id,
        tags = listOf(DockTags.Stack),
        modifier = Modifier.then(
            Modifier.size(100.percent, 100.percent),
        ),
    ) {
        DockTabBar(stack, state, tabContent, allowUndock = true)
        val selected = stack.selectedItem ?: return@Column
        Box(
            id = "${stack.id}-content",
            tags = listOf(DockTags.Content),
            modifier =
                Modifier.size(100.percent, 0.px)
                    .grow(1f)
                    .clip()
        ) {
            key(selected.id) {
                content(selected)
            }
        }
    }
}

@Composable
private fun FloatingDockWindowView(
    window: FloatingDockWindow,
    state: DockingState,
    index: Int,
    tabContent: DockHeaderContent,
    headerContent: DockHeaderContent,
    content: DockItemContent,
) {
    val selected = window.stack.selectedItem ?: return
    Box(
        id = window.id,
        tags = listOf(DockTags.Window),
        modifier = Modifier.position(window.x.px, window.y.px)
            .size(window.width.px, window.height.px)
            .layer(100 + index)
            .input(hoverable = true, clickable = true)
            .onPress {
                state.focus(selected.id)
            }
    ) {
        Column(modifier = Modifier.size(100.percent, 100.percent)) {
            FloatingHeader(window, state, headerContent)
            if (window.stack.items.size > 1) DockTabBar(window.stack, state, tabContent, allowUndock = false)
            Box(
                id = "${window.id}-content",
                tags = listOf(DockTags.Content),
                modifier = Modifier.size(100.percent, 0.px)
                    .grow(1f)
                    .clip()
                    .input(hoverable = true, clickable = true)
                    .onPress {
                        state.focus(selected.id)
                    }
            ) {
                key(selected.id) {
                    content(selected)
                }
            }
        }
        FloatingResizeHandle(window, state)
    }
}

@Composable
private fun FloatingHeader(
    window: FloatingDockWindow,
    state: DockingState,
    headerContent: DockHeaderContent,
) {
    val selected = window.stack.selectedItem ?: return
    Row(
        id = window.dragKey ?: "${window.id}-header",
        tags = listOf(DockTags.Header),
        modifier = Modifier.size(100.percent, 24.px)
            .alignItems(vertical = UiAlign.CENTER)
            .input(hoverable = true, clickable = true, draggable = true)
            .cursor(UiCursorShape.MOVE)
            .onPress {
                state.focus(selected.id)
            }
            .onDrag { event ->
                state.startDraggingWindow(window.id)
                state.moveFloating(window.id, event.deltaX, event.deltaY)
                event.consume()
            }
            .onRelease { event ->
                state.finishDraggingWindow()
            },
    ) {
        Box(
            modifier = Modifier.size(0.px, 100.percent)
                .grow(1f)
                .padding(8.px, 0.px)
        ) {
            headerContent(selected)
        }
        CloseButton(selected, state)
    }
}

@Composable
private fun DockTabBar(
    stack: DockNode.Stack,
    state: DockingState,
    tabContent: DockHeaderContent,
    allowUndock: Boolean,
) {
    val itemIds = stack.items.map { it.id }
    val measurePolicy = remember(stack.id, itemIds) {
        dockTabBarMeasurePolicy(stack.id, itemIds, state)
    }
    Layout(
        content = {
            stack.items.forEachIndexed { index, item ->
                val selected = stack.selectedItem?.id == item.id
                key(item.id) {
                    DockTab(stack.id, index, item, selected, state, tabContent, allowUndock)
                }
            }
        },
        id = "${stack.id}-tabs",
        tags = listOf(DockTags.TabBar),
        modifier = Modifier.then(
            Modifier.size(100.percent, 24.px),
        ),
        measurePolicy = measurePolicy,
    )
}

@Composable
private fun DockTab(
    stackId: String,
    index: Int,
    item: DockItem,
    selected: Boolean,
    state: DockingState,
    tabContent: DockHeaderContent,
    allowUndock: Boolean,
) {
    val dragOffset = state.tabDragOffset(stackId, item.id, index)
    val swapOffset = if (dragOffset == null) state.consumeTabSwapOffset(stackId, item.id) else null
    val tabOffset = dragOffset ?: swapOffset

    val layerIndex = when {
        dragOffset != null -> 50
        selected -> 1
        else -> 0
    }

    Row(
        id = tabNodeId(item.id),
        tags = buildList {
            add(DockTags.Tab)
            if (selected) add(DockTags.Selected)
            if (item.dirty) add(DockTags.Dirty)
        },
        modifier =
            Modifier.size(width = UiLength.Auto, height = DockTabHeight.px)
                .minSize(width = DockTabMinWidth.px)
                .maxSize(width = DockTabMaxWidth.px)
                .alignItems(vertical = UiAlign.CENTER)
                .layer(layerIndex)
                .clip()
                .buildTabTransformModifier(tabOffset, dragOffset, swapOffset)
                .cursor(if (allowUndock) UiCursorShape.MOVE else UiCursorShape.HAND)
                .input(hoverable = true, clickable = true, draggable = true)
                .buildTabInputModifier(stackId, item, state, allowUndock)
    ) {
        TabContentWrapper(item, tabContent)

        CloseButton(item, state)
    }
}

private fun Modifier.buildTabTransformModifier(
    tabOffset: Float?,
    dragOffset: Float?,
    swapOffset: Float?,
): Modifier {
    if (swapOffset != null) {
        then(Modifier.transition())
    }
    if (tabOffset != null) {
        val isDragging = dragOffset != null
        then(
            Modifier.translate(
                x = tabOffset,
                y = if (isDragging) -2f else 0f,
                z = if (isDragging) 10f else 0f
            )
        )
    }
    return this
}

private fun Modifier.buildTabInputModifier(
    stackId: String,
    item: DockItem,
    state: DockingState,
    allowUndock: Boolean,
): Modifier =
    onPress { event ->
        if (event.button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
            if (item.closable) state.close(item.id)
            event.consume()
            return@onPress
        }
        state.select(item.id)
        state.beginTabGrab(stackId, item.id, event.localX, event.localY)
    }.onClick { event ->
        if (event.button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
            if (item.closable) state.close(item.id)
            event.consume()
            return@onClick
        }
        state.select(item.id)
        event.consume()
    }.onDrag { event ->
        val grab = state.tabGrab(stackId, item.id)

        if (event.isInsideTabBar()) {
            state.dragTabInBar(stackId, item.id, event.parentLocalX, grab?.x ?: event.localX)
            event.consume()
            return@onDrag
        }

        if (allowUndock) {
            state.finishTabDrag()
            val spaceX = event.localXInAncestor(DockTags.Space) ?: event.rootLocalX
            val spaceY = event.localYInAncestor(DockTags.Space) ?: event.rootLocalY

            state.beginDraggingTab(
                itemId = item.id,
                x = spaceX - (grab?.x ?: event.localX),
                y = spaceY - (grab?.y ?: event.localY),
            )?.let { dragStart ->
                if (!dragStart.created) {
                    state.moveFloating(dragStart.windowId, event.deltaX, event.deltaY)
                }
            }
            event.consume()
        }
    }.onRelease {
        state.finishTabDrag()
    }


private fun dockTabBarMeasurePolicy(
    stackId: String,
    itemIds: List<String>,
    state: DockingState,
) = UiMeasurePolicy { measurables, constraints ->
    var x = 0f
    val layouts = ArrayList<DockTabLayout>(measurables.size)
    val placeables = measurables.mapIndexed { index, measurable ->
        val placeable = measurable.measure(
            UiConstraints(
                maxWidth = constraints.maxWidth,
                maxHeight = DockTabHeight,
            )
        )
        val itemId = itemIds.getOrNull(index) ?: measurable.node.id.orEmpty()
        layouts += DockTabLayout(
            itemId = itemId,
            left = x + DockTabMargin,
            width = placeable.width,
            outerLeft = x,
            outerWidth = placeable.width + DockTabMargin * 2f,
        )
        x += placeable.width + DockTabMargin * 2f
        placeable
    }
    state.updateTabLayouts(stackId, layouts)
    val width = constraints.maxWidth.takeIf { it.isFinite() } ?: x
    layout(width, DockTabHeight + DockTabMargin * 2f) {
        var childX = 0f
        placeables.forEach { placeable ->
            placeable.place(childX + DockTabMargin, DockTabMargin)
            childX += placeable.width + DockTabMargin * 2f
        }
    }
}

@Composable
private fun TabContentWrapper(item: DockItem, tabContent: DockHeaderContent) {
    Box(
        modifier = Modifier.size(UiLength.Auto, 100.percent)
            .maxSize((DockTabMaxWidth - DockTabCloseWidth).px, 100.percent)
            .padding(8.px, 0.px)
            .clip()
    ) {
        tabContent(item)
    }
}

@Composable
private fun CloseButton(item: DockItem, state: DockingState) {
    if (!item.closable) return
    Box(
        id = "dock-close-${item.id}",
        tags = listOf(DockTags.CloseButton),
        modifier = Modifier.size(DockTabCloseWidth.px, 100.percent)
            .input(hoverable = true, clickable = true)
            .cursor(UiCursorShape.HAND)
            .onClick { event ->
                state.close(item.id)
                event.consume()
            }
    ) {
        Text("x", modifier = Modifier.align(UiAlign.CENTER, UiAlign.CENTER))
    }
}

@Composable
private fun DefaultDockTabContent(item: DockItem) {
    Text(
        item.title.lang,
        modifier = Modifier.align(UiAlign.START, UiAlign.CENTER)
            .textWrap(false)
            .textOverflow(UiTextOverflow.DOTS)
    )
}

@Composable
private fun DefaultDockHeaderContent(item: DockItem) {
    Text(
        item.title.lang,
        modifier = Modifier.align(UiAlign.START, UiAlign.CENTER)
            .textWrap(false)
            .textOverflow(UiTextOverflow.DOTS)
    )
}

private fun splitPaneModifier(horizontal: Boolean, grow: Float): Modifier {
    return Modifier.size(if (horizontal) 0.px else 100.percent, if (horizontal) 100.percent else 0.px)
        .grow(grow.coerceAtLeast(0.001f))

}

private fun UiEvent.isInsideTabBar(): Boolean {
    return parentLocalY >= -8f && parentLocalY <= parentHeight + 8f
}

object DockTags {
    const val Space = "dock-space"
    const val Root = "dock-root"
    const val Split = "dock-split"
    const val Splitter = "dock-splitter"
    const val Stack = "dock-stack"
    const val TabBar = "dock-tab-bar"
    const val Tab = "dock-tab"
    const val Selected = "selected"
    const val Dirty = "dirty"
    const val Header = "dock-header"
    const val Window = "dock-window"
    const val Content = "dock-content"
    const val ResizeHandle = "dock-resize-handle"
    const val CloseButton = "dock-close-button"
    const val DropOverlay = "dock-drop-overlay"
    const val DropZone = "dock-drop-zone"
}

object DockColors {
    val DropZone = UiColor(0.18f, 0.42f, 0.75f, 0.1f)
    val DropZoneActive = UiColor(0.25f, 0.58f, 0.95f, 0.42f)
    val DropZoneBorder = UiColor(0.44f, 0.68f, 0.95f, 0.28f)
    val DropZoneBorderActive = UiColor(0.7f, 0.86f, 1f, 0.95f)
}
