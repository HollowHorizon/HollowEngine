package ru.hollowhorizon.hollowengine.client.ui.docking

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

private const val MinSplitFraction = 0.1f
private const val MaxSplitFraction = 0.9f

class DockingState {
    private val ids = DockIdGenerator()

    var root: DockNode? by mutableStateOf(null)
        private set

    val floatingWindows = mutableStateListOf<FloatingDockWindow>()

    var focusedItemId: String? by mutableStateOf(null)
        private set

    var draggedWindowId: String? by mutableStateOf(null)
        private set

    var previewTarget: DockTarget? by mutableStateOf(null)
        private set

    var tabDrag: DockTabDragState? by mutableStateOf(null)
        private set

    private var tabGrab: DockTabGrabState? by mutableStateOf(null)
    private val tabSwapOffsets = mutableStateMapOf<String, Float>()

    fun open(item: DockItem, target: DockTarget = DockTarget.Root) {
        if (contains(item.id)) {
            focus(item.id)
            return
        }
        val node = DockNode.Stack(ids.nextStackId(), listOf(item), item.id)
        root = root?.insert(node, defaultStackTarget(target), ids) ?: node
        focus(item.id)
    }

    fun openFloating(
        item: DockItem,
        x: Float = 32f,
        y: Float = 32f,
        width: Float = 320f,
        height: Float = 220f,
    ) {
        if (contains(item.id)) {
            focus(item.id)
            return
        }
        val stack = DockNode.Stack(ids.nextStackId(), listOf(item), item.id)
        floatingWindows += FloatingDockWindow(ids.nextWindowId(), stack, x, y, width, height)
        focus(item.id)
    }

    fun close(itemId: String): Boolean {
        val dockedRemoval = root?.removeItem(itemId)
        if (dockedRemoval?.item != null) {
            root = dockedRemoval.root
            if (focusedItemId == itemId) focusedItemId = firstItemId()
            return true
        }

        val index = floatingWindows.indexOfFirst { it.stack.items.any { item -> item.id == itemId } }
        if (index < 0) return false
        val window = floatingWindows[index]
        val remaining = window.stack.items.filterNot { it.id == itemId }
        if (remaining.isEmpty()) {
            floatingWindows.removeAt(index)
        } else {
            val selected = if (window.stack.selectedItemId == itemId) remaining.first().id else window.stack.selectedItemId
            floatingWindows[index] = window.copy(stack = window.stack.copy(items = remaining, selectedItemId = selected))
        }
        if (focusedItemId == itemId) focusedItemId = firstItemId()
        return true
    }

    fun focus(itemId: String): Boolean {
        if (!contains(itemId)) return false
        focusedItemId = itemId
        root = root?.select(itemId)
        val index = floatingWindows.indexOfFirst { it.stack.items.any { item -> item.id == itemId } }
        if (index >= 0) {
            val window = floatingWindows.removeAt(index)
            floatingWindows += window.copy(stack = window.stack.copy(selectedItemId = itemId))
        }
        return true
    }

    fun select(itemId: String): Boolean = focus(itemId)

    fun dock(itemId: String, target: DockTarget): Boolean {
        val removal = removeItemForDock(itemId) ?: return false
        val node = DockNode.Stack(ids.nextStackId(), listOf(removal), removal.id)
        root = root?.insert(node, defaultStackTarget(target), ids) ?: node
        focus(itemId)
        return true
    }

    fun dockWindow(windowId: String, target: DockTarget): Boolean {
        val index = floatingWindows.indexOfFirst { it.id == windowId }
        if (index < 0) return false
        val window = floatingWindows.removeAt(index)
        root = root?.insert(window.stack, defaultStackTarget(target), ids) ?: window.stack
        focus(window.stack.selectedItem?.id ?: window.stack.items.first().id)
        return true
    }

    fun undockToFloating(
        itemId: String,
        x: Float,
        y: Float,
        width: Float = 320f,
        height: Float = 220f,
    ): Boolean {
        val item = removeItemForDock(itemId) ?: return false
        floatingWindows += FloatingDockWindow(
            id = ids.nextWindowId(),
            stack = DockNode.Stack(ids.nextStackId(), listOf(item), item.id),
            x = x,
            y = y,
            width = width,
            height = height,
        )
        focus(itemId)
        return true
    }

    fun beginDraggingTab(
        itemId: String,
        x: Float,
        y: Float,
        width: Float = 320f,
        height: Float = 220f,
    ): DockWindowDragStart? {
        val existingWindow = floatingWindows.firstOrNull { window -> window.stack.items.any { it.id == itemId } }
        if (existingWindow != null) {
            draggedWindowId = existingWindow.id
            return DockWindowDragStart(existingWindow.id, created = false)
        }
        val item = removeItemForDock(itemId) ?: return null
        val window = FloatingDockWindow(
            id = ids.nextWindowId(),
            stack = DockNode.Stack(ids.nextStackId(), listOf(item), item.id),
            x = x,
            y = y,
            width = width.coerceAtLeast(item.minWidth),
            height = height.coerceAtLeast(item.minHeight),
            dragKey = tabNodeId(item.id),
        )
        floatingWindows += window
        draggedWindowId = window.id
        focus(itemId)
        return DockWindowDragStart(window.id, created = true)
    }

    fun moveFloating(windowId: String, deltaX: Float, deltaY: Float): Boolean {
        val index = floatingWindows.indexOfFirst { it.id == windowId }
        if (index < 0) return false
        val window = floatingWindows[index]
        floatingWindows[index] = window.copy(x = window.x + deltaX, y = window.y + deltaY)
        return true
    }

    fun startDraggingWindow(windowId: String) {
        draggedWindowId = windowId
    }

    fun previewDock(target: DockTarget?) {
        previewTarget = target
    }

    fun finishDraggingWindow() {
        val windowId = draggedWindowId
        draggedWindowId = null
        previewTarget = null
        finishTabDrag()
        if (windowId != null) {
            val index = floatingWindows.indexOfFirst { it.id == windowId }
            if (index >= 0 && floatingWindows[index].dragKey != null) {
                floatingWindows[index] = floatingWindows[index].copy(dragKey = null)
            }
        }
    }

    fun dockDraggedWindow(target: DockTarget): Boolean {
        val windowId = draggedWindowId ?: return false
        val docked = dockWindow(windowId, target)
        finishDraggingWindow()
        return docked
    }

    fun resizeFloating(
        windowId: String,
        deltaX: Float,
        deltaY: Float,
        minWidth: Float = 160f,
        minHeight: Float = 120f,
    ): Boolean {
        val index = floatingWindows.indexOfFirst { it.id == windowId }
        if (index < 0) return false
        val window = floatingWindows[index]
        floatingWindows[index] = window.copy(
            width = (window.width + deltaX).coerceAtLeast(minWidth),
            height = (window.height + deltaY).coerceAtLeast(minHeight),
        )
        return true
    }

    fun resizeFloating(
        windowId: String,
        edge: DockResizeEdge,
        deltaX: Float,
        deltaY: Float,
        minWidth: Float = 160f,
        minHeight: Float = 120f,
    ): Boolean {
        val index = floatingWindows.indexOfFirst { it.id == windowId }
        if (index < 0) return false
        val window = floatingWindows[index]
        var nextX = window.x
        var nextY = window.y
        var nextWidth = window.width
        var nextHeight = window.height

        if (edge.resizesLeft) {
            val applied = deltaX.coerceAtMost(window.width - minWidth)
            nextX += applied
            nextWidth -= applied
        }
        if (edge.resizesRight) {
            nextWidth = (nextWidth + deltaX).coerceAtLeast(minWidth)
        }
        if (edge.resizesTop) {
            val applied = deltaY.coerceAtMost(window.height - minHeight)
            nextY += applied
            nextHeight -= applied
        }
        if (edge.resizesBottom) {
            nextHeight = (nextHeight + deltaY).coerceAtLeast(minHeight)
        }

        floatingWindows[index] = window.copy(x = nextX, y = nextY, width = nextWidth, height = nextHeight)
        return true
    }

    fun setSplitFraction(splitId: String, fraction: Float): Boolean {
        val current = root ?: return false
        var changed = false
        root = current.mapSplits { split ->
            if (split.id == splitId) {
                changed = true
                split.withFractionPreservingChildren(fraction.coerceIn(MinSplitFraction, MaxSplitFraction))
            } else {
                split
            }
        }
        return changed
    }

    fun resizeSplitByFraction(splitId: String, deltaFraction: Float): Boolean {
        val split = root?.findNode(splitId) as? DockNode.Split ?: return false
        return setSplitFraction(splitId, split.fraction + deltaFraction)
    }

    fun dragTabInBar(
        stackId: String,
        itemId: String,
        pointerX: Float,
        grabX: Float,
        tabWidth: Float,
    ): Boolean {
        tabDrag = DockTabDragState(stackId, itemId, pointerX, grabX, tabWidth)
        val stack = findStack(stackId) ?: return false
        val currentIndex = stack.items.indexOfFirst { it.id == itemId }
        if (currentIndex < 0) return false
        val targetIndex = tabDragTargetIndex(
            currentIndex = currentIndex,
            itemCount = stack.items.size,
            draggedLeft = pointerX - grabX,
            tabWidth = tabWidth,
        )
        if (targetIndex == currentIndex) return false
        val previousOrder = stack.items.map { it.id }
        val changed = reorderTab(stackId, itemId, targetIndex)
        if (changed) {
            val nextOrder = findStack(stackId)?.items?.map { it.id }.orEmpty()
            recordTabSwapOffsets(stackId, itemId, previousOrder, nextOrder, tabWidth)
        }
        return changed
    }

    fun beginTabGrab(stackId: String, itemId: String, x: Float, y: Float) {
        tabGrab = DockTabGrabState(stackId, itemId, x, y)
    }

    fun tabGrab(stackId: String, itemId: String): DockTabGrabState? {
        return tabGrab?.takeIf { it.stackId == stackId && it.itemId == itemId }
    }

    fun finishTabDrag() {
        tabDrag = null
        tabGrab = null
    }

    fun tabDragOffset(stackId: String, itemId: String, currentIndex: Int): Float? {
        val drag = tabDrag ?: return null
        if (drag.stackId != stackId || drag.itemId != itemId) return null
        return drag.pointerX - drag.grabX - currentIndex * drag.tabWidth
    }

    fun consumeTabSwapOffset(stackId: String, itemId: String): Float? {
        return tabSwapOffsets.remove(tabSwapOffsetKey(stackId, itemId))
    }

    fun reorderTab(stackId: String, itemId: String, targetIndex: Int): Boolean {
        var changed = false
        root = root?.let { node ->
            val next = node.reorderTab(stackId, itemId, targetIndex)
            changed = changed || next != node
            next
        }
        for (index in floatingWindows.indices) {
            val window = floatingWindows[index]
            if (window.stack.id != stackId) continue
            val nextStack = window.stack.reorderTab(stackId, itemId, targetIndex) as DockNode.Stack
            changed = changed || nextStack != window.stack
            floatingWindows[index] = window.copy(stack = nextStack)
        }
        return changed
    }

    fun contains(itemId: String): Boolean {
        return root?.containsItem(itemId) == true || floatingWindows.any { window ->
            window.stack.items.any { it.id == itemId }
        }
    }

    private fun removeItemForDock(itemId: String): DockItem? {
        val dockedRemoval = root?.removeItem(itemId)
        if (dockedRemoval?.item != null) {
            root = dockedRemoval.root
            return dockedRemoval.item
        }

        val windowIndex = floatingWindows.indexOfFirst { it.stack.items.any { item -> item.id == itemId } }
        if (windowIndex < 0) return null
        val window = floatingWindows[windowIndex]
        val item = window.stack.items.first { it.id == itemId }
        val remaining = window.stack.items.filterNot { it.id == itemId }
        if (remaining.isEmpty()) {
            floatingWindows.removeAt(windowIndex)
        } else {
            floatingWindows[windowIndex] = window.copy(
                stack = window.stack.copy(
                    items = remaining,
                    selectedItemId = remaining.firstOrNull { it.id == window.stack.selectedItemId }?.id
                        ?: remaining.first().id,
                )
            )
        }
        return item
    }

    private fun firstItemId(): String? {
        return root?.firstItemId() ?: floatingWindows.firstOrNull()?.stack?.items?.firstOrNull()?.id
    }

    private fun defaultStackTarget(target: DockTarget): DockTarget {
        if (target.anchorId != null || target.placement != DockPlacement.CENTER) return target
        val anchor = focusedItemId
            ?.let { root?.findStackWithItem(it)?.id }
            ?: root?.firstStackId()
        return anchor?.let { target.copy(anchorId = it) } ?: target
    }

    private fun findStack(stackId: String): DockNode.Stack? {
        return root?.findNode(stackId) as? DockNode.Stack
            ?: floatingWindows.firstOrNull { it.stack.id == stackId }?.stack
    }

    private fun recordTabSwapOffsets(
        stackId: String,
        draggedItemId: String,
        previousOrder: List<String>,
        nextOrder: List<String>,
        tabWidth: Float,
    ) {
        nextOrder.forEachIndexed { nextIndex, itemId ->
            if (itemId == draggedItemId) return@forEachIndexed
            val previousIndex = previousOrder.indexOf(itemId)
            if (previousIndex < 0 || previousIndex == nextIndex) return@forEachIndexed
            tabSwapOffsets[tabSwapOffsetKey(stackId, itemId)] = (previousIndex - nextIndex) * tabWidth
        }
    }
}

private fun tabSwapOffsetKey(stackId: String, itemId: String): String = "$stackId/$itemId"

private fun tabDragTargetIndex(
    currentIndex: Int,
    itemCount: Int,
    draggedLeft: Float,
    tabWidth: Float,
): Int {
    val currentLeft = currentIndex * tabWidth
    val currentRight = currentLeft + tabWidth
    val draggedRight = draggedLeft + tabWidth
    if (draggedRight > currentRight) {
        var targetIndex = currentIndex
        while (targetIndex < itemCount - 1) {
            val nextMidpoint = (targetIndex + 1) * tabWidth + tabWidth * 0.5f
            if (draggedRight < nextMidpoint) break
            targetIndex++
        }
        return targetIndex
    }
    if (draggedLeft < currentLeft) {
        var targetIndex = currentIndex
        while (targetIndex > 0) {
            val previousMidpoint = (targetIndex - 1) * tabWidth + tabWidth * 0.5f
            if (draggedLeft > previousMidpoint) break
            targetIndex--
        }
        return targetIndex
    }
    return currentIndex
}

private fun DockNode.Split.withFractionPreservingChildren(nextFraction: Float): DockNode.Split {
    val oldFraction = fraction
    if (oldFraction == nextFraction) return this
    return copy(
        fraction = nextFraction,
        first = first.preserveSplitPosition(orientation, oldSpan = oldFraction, nextSpan = nextFraction, startShift = 0f),
        second = second.preserveSplitPosition(
            orientation = orientation,
            oldSpan = 1f - oldFraction,
            nextSpan = 1f - nextFraction,
            startShift = oldFraction - nextFraction,
        ),
    )
}

private fun DockNode.preserveSplitPosition(
    orientation: DockOrientation,
    oldSpan: Float,
    nextSpan: Float,
    startShift: Float,
): DockNode {
    if (oldSpan <= 0f || nextSpan <= 0f) return this
    if (this !is DockNode.Split) return this
    if (this.orientation != orientation) {
        return copy(
            first = first.preserveSplitPosition(orientation, oldSpan, nextSpan, startShift),
            second = second.preserveSplitPosition(orientation, oldSpan, nextSpan, startShift),
        )
    }
    val oldFraction = fraction
    val oldBoundary = startShift + oldSpan * oldFraction
    val nextFraction = (oldBoundary / nextSpan).coerceIn(MinSplitFraction, MaxSplitFraction)
    return copy(
        fraction = nextFraction,
        first = first.preserveSplitPosition(
            orientation = orientation,
            oldSpan = oldSpan * oldFraction,
            nextSpan = nextSpan * nextFraction,
            startShift = startShift,
        ),
        second = second.preserveSplitPosition(
            orientation = orientation,
            oldSpan = oldSpan * (1f - oldFraction),
            nextSpan = nextSpan * (1f - nextFraction),
            startShift = startShift + oldSpan * oldFraction - nextSpan * nextFraction,
        ),
    )
}

private val DockResizeEdge.resizesLeft: Boolean
    get() = this == DockResizeEdge.LEFT || this == DockResizeEdge.TOP_LEFT || this == DockResizeEdge.BOTTOM_LEFT

private val DockResizeEdge.resizesRight: Boolean
    get() = this == DockResizeEdge.RIGHT || this == DockResizeEdge.TOP_RIGHT || this == DockResizeEdge.BOTTOM_RIGHT

private val DockResizeEdge.resizesTop: Boolean
    get() = this == DockResizeEdge.TOP || this == DockResizeEdge.TOP_LEFT || this == DockResizeEdge.TOP_RIGHT

private val DockResizeEdge.resizesBottom: Boolean
    get() = this == DockResizeEdge.BOTTOM || this == DockResizeEdge.BOTTOM_LEFT || this == DockResizeEdge.BOTTOM_RIGHT

private fun DockNode.firstItemId(): String? {
    return when (this) {
        is DockNode.Stack -> items.firstOrNull()?.id
        is DockNode.Split -> first.firstItemId() ?: second.firstItemId()
    }
}

private fun DockNode.firstStackId(): String? {
    return when (this) {
        is DockNode.Stack -> id
        is DockNode.Split -> first.firstStackId() ?: second.firstStackId()
    }
}

private fun DockNode.mapSplits(transform: (DockNode.Split) -> DockNode.Split): DockNode {
    return when (this) {
        is DockNode.Stack -> this
        is DockNode.Split -> transform(copy(first = first.mapSplits(transform), second = second.mapSplits(transform)))
    }
}

internal fun tabNodeId(itemId: String): String = "dock-tab-$itemId"
