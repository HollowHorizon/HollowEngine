package ru.hollowhorizon.hollowengine.client.ui.docking

private const val MinPinnedWidth = 60f
private const val MaxPinnedWidth = 900f
private const val MinBottomHeight = 60f
private const val MaxBottomHeight = 900f
private const val MinBottomFraction = 0.15f
private const val MaxBottomFraction = 0.85f
private const val DefaultSideSplitFraction = 0.5f
private const val MinSideSplitFraction = 0.12f
private const val MaxSideSplitFraction = 0.88f

/** The stripe buttons on [side], in the order they are drawn. */
fun DockingState.pinnedOn(side: DockSide): List<DockPinnedItem> =
    DockStripeGroup.entries.flatMap { group -> pinnedIn(DockAnchor(side, group)) }

fun DockingState.pinnedIn(anchor: DockAnchor): List<DockPinnedItem> = pinnedItems.filter { it.anchor == anchor }

/** The window open in [anchor], or null while that half is collapsed. */
fun DockingState.expandedIn(anchor: DockAnchor): DockPinnedItem? {
    val itemId = expandedByAnchor[anchor] ?: return null
    return pinnedItems.firstOrNull { it.item.id == itemId && it.anchor == anchor }
}

/** The window open in the top part of [side]'s stripe. */
fun DockingState.expandedOn(side: DockSide): DockPinnedItem? = expandedIn(DockAnchor(side))

/** What the side panel of [side] shows, top to bottom: its top window, then the split one under it. */
fun DockingState.sidePanels(side: DockSide): List<DockPinnedItem> = listOfNotNull(
    expandedIn(DockAnchor(side, DockStripeGroup.TOP)),
    expandedIn(DockAnchor(side, DockStripeGroup.SPLIT)),
)

/** The side panel's width; the windows in it share one, the top one's when both are open. */
fun DockingState.sideWidth(side: DockSide): Float? = sidePanels(side).firstOrNull()?.width

fun DockingState.sideSplitFraction(side: DockSide): Float = sideSplitFractions[side] ?: DefaultSideSplitFraction

fun DockingState.setSideSplitFraction(side: DockSide, fraction: Float) {
    sideSplitFractions[side] = fraction.coerceIn(MinSideSplitFraction, MaxSideSplitFraction)
}

/** Sets the width of every window the side panel shows, so they stay one column. */
fun DockingState.setSideWidth(side: DockSide, width: Float) {
    sidePanels(side).forEach { setPinnedWidth(it.item.id, width) }
}

/** Swaps the side panel's two windows, keeping both open. */
fun DockingState.swapSidePanels(side: DockSide): Boolean {
    val top = expandedIn(DockAnchor(side, DockStripeGroup.TOP)) ?: return false
    val split = expandedIn(DockAnchor(side, DockStripeGroup.SPLIT)) ?: return false
    val topIndex = pinnedItems.indexOf(top)
    val splitIndex = pinnedItems.indexOf(split)
    pinnedItems[topIndex] = split.copy(group = DockStripeGroup.TOP, width = top.width)
    pinnedItems[splitIndex] = top.copy(group = DockStripeGroup.SPLIT)
    expandedByAnchor[DockAnchor(side, DockStripeGroup.TOP)] = split.item.id
    expandedByAnchor[DockAnchor(side, DockStripeGroup.SPLIT)] = top.item.id
    return true
}

fun DockingState.pinnedItem(itemId: String): DockPinnedItem? = pinnedItems.firstOrNull { it.item.id == itemId }

fun DockingState.isPinned(itemId: String): Boolean = pinnedItem(itemId) != null

/**
 * Parks a tool window on a stripe and opens it there.
 */
fun DockingState.pin(itemId: String, side: DockSide, group: DockStripeGroup = DockStripeGroup.TOP): Boolean {
    val anchor = DockAnchor(side, group)
    if (isPinned(itemId)) {
        movePinned(itemId, anchor, pinnedIn(anchor).size)
        return expand(itemId)
    }

    val item = item(itemId) ?: return false
    if (!item.pinnable) return false
    removeItemForDock(itemId) ?: return false
    pinnedItems += DockPinnedItem(item, side, DefaultPinnedWidth.coerceAtLeast(item.minWidth), group)
    stripesVisible = true
    return expand(itemId)
}

/**
 * Moves a parked window to [index] among the buttons of [anchor].
 */
fun DockingState.movePinned(itemId: String, anchor: DockAnchor, index: Int): Boolean {
    val current = pinnedItem(itemId) ?: return false
    val wasOpen = expandedByAnchor[current.anchor] == itemId
    val others = pinnedItems.filterNot { it.item.id == itemId }
    val moved = current.copy(side = anchor.side, group = anchor.group)

    val before = others.filter { it.anchor == anchor }.getOrNull(index.coerceAtLeast(0))
    val position = before?.let { others.indexOf(it) } ?: run {
        val lastOfAnchor = others.indexOfLast { it.anchor == anchor }
        if (lastOfAnchor >= 0) lastOfAnchor + 1 else others.size
    }
    val next = others.toMutableList().apply { add(position, moved) }
    if (next == pinnedItems.toList()) return false

    pinnedItems.clear()
    pinnedItems += next
    if (wasOpen && current.anchor != anchor) {
        expandedByAnchor.remove(current.anchor)
        expandedByAnchor[anchor] = itemId
    }
    return true
}

/** Takes a pinned item off its stripe and docks it back into the tree at [target]. */
fun DockingState.unpin(itemId: String, target: DockTarget = DockTarget.Root): Boolean {
    val pinned = takeOffStripe(itemId) ?: return false
    open(pinned.item, target)
    return true
}

/**
 * Takes a pinned item off its stripe into a floating window that is being dragged.
 */
fun DockingState.undockPinned(itemId: String, x: Float, y: Float, dragKey: String): DockWindowDragStart? {
    val pinned = takeOffStripe(itemId) ?: return null
    val item = pinned.item
    val stack = newStack(listOf(item), item.id) ?: return null
    val window = newWindow(
        stack,
        x,
        y,
        pinned.width.coerceAtLeast(item.minWidth),
        FloatingHeight.coerceAtLeast(item.minHeight),
    ).copy(dragKey = dragKey)
    floatingWindows += window
    startDraggingWindow(window.id)
    focus(item.id)
    return DockWindowDragStart(window.id, created = true)
}

/** Whether the window being dragged could be parked on a stripe. */
fun DockingState.canPinDraggedWindow(): Boolean {
    val window = floatingWindows.firstOrNull { it.id == draggedWindowId } ?: return false
    return window.stack.items.all { it.pinnable }
}

/** Parks every window of the dragged floating window in [anchor], and opens the one on show. */
fun DockingState.pinDraggedWindow(anchor: DockAnchor): Boolean {
    val index = floatingWindows.indexOfFirst { it.id == draggedWindowId }
    if (index < 0 || !canPinDraggedWindow()) {
        finishDraggingWindow()
        return false
    }
    val window = floatingWindows.removeAt(index)
    window.stack.items.forEach { item ->
        pinnedItems += DockPinnedItem(item, anchor.side, window.width.coerceAtLeast(item.minWidth), anchor.group)
    }
    stripesVisible = true
    finishDraggingWindow()
    return expand(window.stack.selectedItem?.id ?: window.stack.items.first().id)
}

private fun DockingState.takeOffStripe(itemId: String): DockPinnedItem? {
    val index = pinnedItems.indexOfFirst { it.item.id == itemId }
    if (index < 0) return null
    val pinned = pinnedItems.removeAt(index)
    if (expandedByAnchor[pinned.anchor] == itemId) expandedByAnchor.remove(pinned.anchor)
    return pinned
}

/** Opens a pinned item's panel, collapsing whatever its half of the stripe had open. */
fun DockingState.expand(itemId: String): Boolean {
    val pinned = pinnedItem(itemId) ?: return false
    expandedByAnchor[pinned.anchor] = itemId
    focusedItemId = itemId
    return true
}

/** Collapses [anchor] back to its bare buttons. */
fun DockingState.collapse(anchor: DockAnchor) {
    val itemId = expandedByAnchor.remove(anchor) ?: return
    if (focusedItemId == itemId) focusedItemId = firstItemId()
}

/** Is the current panel visible regardless of its mode. */
fun DockingState.isOnScreen(itemId: String): Boolean {
    val pinned = pinnedItem(itemId) ?: return contains(itemId)
    return expandedByAnchor[pinned.anchor] == itemId
}

/** Turns a window off, or back on, wherever it lives. */
fun DockingState.toggleOnScreen(itemId: String): Boolean {
    if (isPinned(itemId)) return togglePinned(itemId)
    if (!contains(itemId)) return false
    return close(itemId)
}

/** Opens that panel, or closes it if it was already open. */
fun DockingState.togglePinned(itemId: String): Boolean {
    val pinned = pinnedItem(itemId) ?: return false
    if (expandedByAnchor[pinned.anchor] == itemId) {
        collapse(pinned.anchor)
        return true
    }
    return expand(itemId)
}

/** Remembers the width the user dragged a side panel to. */
fun DockingState.setPinnedWidth(itemId: String, width: Float): Boolean {
    val index = pinnedItems.indexOfFirst { it.item.id == itemId }
    if (index < 0) return false
    val pinned = pinnedItems[index]
    val next = width.coerceIn(pinned.item.minWidth.coerceAtLeast(MinPinnedWidth), MaxPinnedWidth)
    if (next == pinned.width) return false
    pinnedItems[index] = pinned.copy(width = next)
    return true
}

fun DockingState.setBottomHeight(height: Float) {
    bottomHeight = height.coerceIn(MinBottomHeight, MaxBottomHeight)
}

fun DockingState.setBottomFraction(fraction: Float) {
    bottomFraction = fraction.coerceIn(MinBottomFraction, MaxBottomFraction)
}

/** Restores the stripes from a stored layout, dropping items the tree already holds. */
fun DockingState.applyPinned(
    items: List<DockPinnedItem>,
    expanded: Map<DockAnchor, String>,
    visible: Boolean = true,
    bottomHeight: Float = DefaultBottomHeight,
    bottomFraction: Float = 0.5f,
    sideSplit: Map<DockSide, Float> = emptyMap(),
) {
    pinnedItems.clear()
    pinnedItems += items.filterNot { contains(it.item.id) }
    expandedByAnchor.clear()
    expanded.forEach { (anchor, itemId) ->
        if (pinnedItems.any { it.item.id == itemId && it.anchor == anchor }) expandedByAnchor[anchor] = itemId
    }
    stripesVisible = visible
    setBottomHeight(bottomHeight)
    setBottomFraction(bottomFraction)
    sideSplitFractions.clear()
    sideSplit.forEach { (side, fraction) -> setSideSplitFraction(side, fraction) }
}

/** Which item each half has open, for storing the layout. */
fun DockingState.expandedPinned(): Map<DockAnchor, String> = expandedByAnchor.toMap()

private const val FloatingHeight = 220f
