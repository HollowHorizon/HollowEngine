package ru.hollowhorizon.hollowengine.client.ui.docking

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import ru.hollowhorizon.hollowengine.client.ui.*
import ru.hollowhorizon.hollowengine.client.ui.hss.compileHss

private const val DockTabWidth = 100f
private const val DockTabHeight = 18f
private const val DockTabMargin = 2f
private const val DockTabStride = DockTabWidth + DockTabMargin * 2f

private val DockTabStyle = compileHss(
    """
    .dock-tab {
        background: rgba(34, 37, 45, 1);
        border: 1px rgba(78, 84, 96, 0.9);
        border-radius: 7px;
        shadow: 0px 3px 9px -5px rgba(0, 0, 0, 0.5);
        align: start center;
        transition:
            background 120ms ease-out,
            translate 140ms ease-out,
            shadow 140ms ease-out;
    }

    .dock-tab:hover {
        translate: 0px -1px 0px;
        shadow: 0px 3px 9px -5px rgba(0.3, 0.3, 0.7, 0.25);
    }

    .dock-tab:selected {
        background: rgba(48, 54, 65, 1);
        border: 1px rgba(106, 119, 139, 1);
        shadow: 0px 7px 14px -6px rgba(0, 0, 0, 0.62);
    }

    .dock-tab:dragging {
        translate: 0px -2px 10px;
        shadow: 0px 5px 9px -5px rgba(0, 0, 0, 0.5);
        transition: none;
    }
    """.trimIndent(),
)

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
        modifier = Modifier.then(
            modifier,
            Modifier.clip(),
        ),
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
    val size = 6.px
    Box(
        id = "${split.id}-splitter",
        tags = listOf(DockTags.Splitter),
        modifier = Modifier.then(
            Modifier.size(if (horizontal) size else 100.percent, if (horizontal) 100.percent else size),
            Modifier.background(DockColors.Splitter),
            Modifier.input(hoverable = true, draggable = true),
            Modifier.cursor(if (horizontal) UiCursorShape.RESIZE_HORIZONTAL else UiCursorShape.RESIZE_VERTICAL),
            Modifier.onDrag { event ->
                val parentSize = if (horizontal) event.parentWidth else event.parentHeight
                val splitterSize = size.value
                val paneSize = parentSize - splitterSize
                if (paneSize > 0f) {
                    val delta = if (horizontal) event.deltaX else event.deltaY
                    state.resizeSplitByFraction(split.id, delta / paneSize)
                }
                event.consume()
            },
        ),
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
            Modifier.background(DockColors.Panel),
            Modifier.border(1.px, DockColors.Border),
        ),
    ) {
        DockTabBar(stack, state, tabContent, allowUndock = true)
        val selected = stack.selectedItem ?: return@Column
        Box(
            id = "${stack.id}-content",
            tags = listOf(DockTags.Content),
            modifier = Modifier.then(
                Modifier.size(100.percent, 0.px),
                Modifier.grow(1f),
                Modifier.clip(),
            ),
        ) {
            content(selected)
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
        modifier = Modifier.then(
            Modifier.position(window.x.px, window.y.px),
            Modifier.size(window.width.px, window.height.px),
            Modifier.layer(100 + index),
            Modifier.background(DockColors.Panel),
            Modifier.border(1.px, DockColors.Border),
            Modifier.input(hoverable = true, clickable = true),
            Modifier.onPress {
                state.focus(selected.id)
            },
        ),
    ) {
        Column(modifier = Modifier.size(100.percent, 100.percent)) {
            FloatingHeader(window, state, headerContent)
            if (window.stack.items.size > 1) DockTabBar(window.stack, state, tabContent, allowUndock = false)
            Box(
                id = "${window.id}-content",
                tags = listOf(DockTags.Content),
                modifier = Modifier.then(
                    Modifier.size(100.percent, 0.px),
                    Modifier.grow(1f),
                    Modifier.clip(),
                    Modifier.input(hoverable = true, clickable = true),
                    Modifier.onPress {
                        state.focus(selected.id)
                    },
                ),
            ) {
                content(selected)
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
        modifier = Modifier.then(
            Modifier.size(100.percent, 24.px),
            Modifier.alignItems(vertical = UiAlign.CENTER),
            Modifier.background(DockColors.Header),
            Modifier.input(hoverable = true, clickable = true, draggable = true),
            Modifier.cursor(UiCursorShape.MOVE),
            Modifier.onPress { event ->
                state.focus(selected.id)
            },
            Modifier.onDrag { event ->
                state.startDraggingWindow(window.id)
                state.moveFloating(window.id, event.deltaX, event.deltaY)
                event.consume()
            },
            Modifier.onRelease { event ->
                state.finishDraggingWindow()
                event.consume()
            },
        ),
    ) {
        Box(
            modifier = Modifier.then(
                Modifier.size(0.px, 100.percent),
                Modifier.grow(1f),
                Modifier.padding(8.px, 0.px),
            ),
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
    Row(
        id = "${stack.id}-tabs",
        tags = listOf(DockTags.TabBar),
        modifier = Modifier.then(
            Modifier.size(100.percent, 24.px),
            Modifier.background(DockColors.Header),
        ),
    ) {
        stack.items.forEachIndexed { index, item ->
            val selected = stack.selectedItem?.id == item.id
            key(item.id) {
                DockTab(stack.id, stack.items.size, index, item, selected, state, tabContent, allowUndock)
            }
        }
    }
}

@Composable
private fun DockTab(
    stackId: String,
    tabCount: Int,
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
    Row(
        id = tabNodeId(item.id),
        tags = if (selected) listOf(DockTags.Tab, DockTags.Selected) else listOf(DockTags.Tab),
        modifier = Modifier.then(
            Modifier.style(DockTabStyle),
            Modifier.size(width = DockTabWidth.px, height = DockTabHeight.px),
            Modifier.margin(DockTabMargin.px),
            Modifier.alignItems(vertical = UiAlign.CENTER),
            Modifier.layer(if (dragOffset != null) 50 else if (selected) 1 else 0),
            if (swapOffset != null) Modifier.transition() else Modifier.then(),
            if (tabOffset != null) Modifier.translate(tabOffset, if (dragOffset != null) -2f else 0f, if (dragOffset != null) 10f else 0f) else Modifier.then(),
            Modifier.input(hoverable = true, clickable = true, draggable = true),
            Modifier.cursor(if (allowUndock) UiCursorShape.MOVE else UiCursorShape.HAND),
            Modifier.onPress { event ->
                state.select(item.id)
                state.beginTabGrab(stackId, item.id, event.localX, event.localY)
            },
            Modifier.onClick { event ->
                state.select(item.id)
                event.consume()
            },
            Modifier.onDrag { event ->
                val grab = state.tabGrab(stackId, item.id)
                if (event.isInsideTabBar()) {
                    state.dragTabInBar(stackId, item.id, event.parentLocalX, grab?.x ?: event.localX, DockTabStride)
                    event.consume()
                    return@onDrag
                }
                if (allowUndock) {
                    state.finishTabDrag()
                    val dockSpaceLocalX = event.localXInAncestor(DockTags.Space) ?: event.rootLocalX
                    val dockSpaceLocalY = event.localYInAncestor(DockTags.Space) ?: event.rootLocalY
                    state.beginDraggingTab(
                        item.id,
                        dockSpaceLocalX - (grab?.x ?: event.localX),
                        dockSpaceLocalY - (grab?.y ?: event.localY),
                    )
                        ?.let { dragStart ->
                            if (!dragStart.created) state.moveFloating(dragStart.windowId, event.deltaX, event.deltaY)
                        }
                    event.consume()
                }
            },
            Modifier.onRelease {
                state.finishTabDrag()
            },
        ),
    ) {
        Box(
            modifier = Modifier.then(
                Modifier.size(0.px, 100.percent),
                Modifier.grow(1f),
                Modifier.padding(8.px, 0.px),
            ),
        ) {
            tabContent(item)
        }
        CloseButton(item, state)
    }
}

@Composable
private fun CloseButton(item: DockItem, state: DockingState) {
    if (!item.closable) return
    Box(
        id = "dock-close-${item.id}",
        tags = listOf(DockTags.CloseButton),
        modifier = Modifier.then(
            Modifier.size(22.px, 100.percent),
            Modifier.input(hoverable = true, clickable = true),
            Modifier.cursor(UiCursorShape.HAND),
            Modifier.onClick { event ->
                state.close(item.id)
                event.consume()
            },
        ),
    ) {
        Text("x", modifier = Modifier.align(UiAlign.CENTER, UiAlign.CENTER))
    }
}

@Composable
private fun DefaultDockTabContent(item: DockItem) {
    Text(item.title, modifier = Modifier.align(UiAlign.START, UiAlign.CENTER))
}

@Composable
private fun DefaultDockHeaderContent(item: DockItem) {
    Text(item.title, modifier = Modifier.align(UiAlign.START, UiAlign.CENTER))
}

private fun splitPaneModifier(horizontal: Boolean, grow: Float): Modifier {
    return Modifier.then(
        Modifier.size(if (horizontal) 0.px else 100.percent, if (horizontal) 100.percent else 0.px),
        Modifier.grow(grow.coerceAtLeast(0.001f)),
    )
}

private fun UiEvent.isInsideTabBar(): Boolean {
    return parentLocalY >= -8f && parentLocalY <= parentHeight + 8f
}

private fun tabIndexAt(parentLocalX: Float, tabCount: Int): Int {
    return (parentLocalX / DockTabStride).toInt().coerceIn(0, tabCount - 1)
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
    const val Header = "dock-header"
    const val Window = "dock-window"
    const val Content = "dock-content"
    const val ResizeHandle = "dock-resize-handle"
    const val CloseButton = "dock-close-button"
    const val DropOverlay = "dock-drop-overlay"
    const val DropZone = "dock-drop-zone"
}

object DockColors {
    val Panel = UiColor(0.07f, 0.08f, 0.1f, 1f)
    val Header = UiColor(0.11f, 0.12f, 0.15f, 1f)
    val Tab = UiColor(0.13f, 0.14f, 0.17f, 1f)
    val SelectedTab = UiColor(0.18f, 0.2f, 0.24f, 1f)
    val Border = UiColor(0.26f, 0.28f, 0.32f, 1f)
    val Splitter = UiColor(0.24f, 0.26f, 0.3f, 1f)
    val DropZone = UiColor(0.18f, 0.42f, 0.75f, 0.1f)
    val DropZoneActive = UiColor(0.25f, 0.58f, 0.95f, 0.42f)
    val DropZoneBorder = UiColor(0.44f, 0.68f, 0.95f, 0.28f)
    val DropZoneBorderActive = UiColor(0.7f, 0.86f, 1f, 0.95f)
}
