package ru.hollowhorizon.hollowengine.client.ui.docking

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.delay
import ru.hollowhorizon.hollowengine.client.ui.Box
import ru.hollowhorizon.hollowengine.client.ui.Column
import ru.hollowhorizon.hollowengine.client.ui.Image
import ru.hollowhorizon.hollowengine.client.ui.Modifier
import ru.hollowhorizon.hollowengine.client.ui.Row
import ru.hollowhorizon.hollowengine.client.ui.Text
import ru.hollowhorizon.hollowengine.client.ui.UiAlign
import ru.hollowhorizon.hollowengine.client.ui.UiCursorShape
import ru.hollowhorizon.hollowengine.client.ui.UiPopupAlignment
import ru.hollowhorizon.hollowengine.client.ui.align
import ru.hollowhorizon.hollowengine.client.ui.alignItems
import ru.hollowhorizon.hollowengine.client.ui.clip
import ru.hollowhorizon.hollowengine.client.ui.cursor
import ru.hollowhorizon.hollowengine.client.ui.grow
import ru.hollowhorizon.hollowengine.client.ui.input
import ru.hollowhorizon.hollowengine.client.ui.isRightClick
import ru.hollowhorizon.hollowengine.client.ui.layout.UiRect
import ru.hollowhorizon.hollowengine.client.ui.onDrag
import ru.hollowhorizon.hollowengine.client.ui.onPlaced
import ru.hollowhorizon.hollowengine.client.ui.onPress
import ru.hollowhorizon.hollowengine.client.ui.onRelease
import ru.hollowhorizon.hollowengine.client.ui.percent
import ru.hollowhorizon.hollowengine.client.ui.px
import ru.hollowhorizon.hollowengine.client.ui.size
import ru.hollowhorizon.hollowengine.client.ui.textWrap
import ru.hollowhorizon.hollowengine.client.ui.widgets.ContextMenu
import ru.hollowhorizon.hollowengine.client.ui.widgets.UiDropdownItem
import ru.hollowhorizon.hollowengine.client.ui.widgets.tooltipOnHover
import ru.hollowhorizon.hollowengine.client.utils.lang
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.time.Duration.Companion.milliseconds

/** Edge of a stripe button. */
private const val StripeButtonSize = 22f

/** A button and the gap after it, as `.dock-stripe` lays them out. */
private const val StripeButtonPitch = 24f

/** How long a panel takes to push the editor aside, and to give the room back. */
private const val PanelSlideMillis = 160L

/** Width of the handles that drag a panel's inner edge. */
internal const val PanelSplitterWidth = 3f

/** How far a stripe button has to move before a press becomes a drag rather than a click. */
private const val DragThreshold = 4f

/** How far past its stripe a button has to be dragged to come off it as a floating window. */
private const val UndockDistance = 28f

internal fun DockingState.edgeWidth(side: DockSide): Float {
    val stripe = if (stripesVisible && pinnedOn(side).isNotEmpty()) StripeButtonSize else 0f
    val panel = expandedOn(side)?.let { it.width + PanelSplitterWidth } ?: 0f
    return stripe + panel
}

internal fun stripeId(side: DockSide): String = "dock-stripe-${side.tag}"

internal fun stripeButtonId(itemId: String): String = "dock-stripe-button-$itemId"

/** The buttons of the top half, a gap, then buttons of the bottom half. */
@Composable
internal fun DockStripe(side: DockSide, state: DockingState) {
    val open = state.stripesVisible && state.pinnedOn(side).isNotEmpty()
    var height by remember { mutableStateOf(0f) }
    Column(
        id = stripeId(side),
        tags = listOf(DockTags.Stripe, side.tag),
        modifier = Modifier.size(if (open) StripeButtonSize.px else 0.px, 100.percent)
            .alignItems(horizontal = UiAlign.CENTER).clip().onPlaced { height = it.height },
    ) {
        state.pinnedIn(DockAnchor(side, DockStripeGroup.TOP)).forEach { pinned ->
            key(pinned.item.id) { DockStripeButton(pinned, state) { height } }
        }
        Box(modifier = Modifier.size(1.px, 0.px).grow(1f))
        state.pinnedIn(DockAnchor(side, DockStripeGroup.BOTTOM)).forEach { pinned ->
            key(pinned.item.id) { DockStripeButton(pinned, state) { height } }
        }
    }
}

/**
 * A stripe button. A click opens or closes its window. Dragged along the stripe it moves, into the
 * other half as well.
 */
@Composable
private fun DockStripeButton(pinned: DockPinnedItem, state: DockingState, stripeHeight: () -> Float) {
    val item = pinned.item
    val expanded = state.expandedIn(pinned.anchor)?.item?.id == item.id
    var anchor by remember { mutableStateOf(UiRect.Zero) }
    var menu by remember { mutableStateOf(false) }
    val dragging = remember { booleanArrayOf(false) }

    Box(
        id = stripeButtonId(item.id),
        tags = buildList {
            add(DockTags.StripeButton)
            if (expanded) add(DockTags.Selected)
        },
        modifier = Modifier.size(StripeButtonSize.px, StripeButtonSize.px)
            .input(hoverable = true, clickable = true, draggable = true)
            .cursor(UiCursorShape.HAND).tooltipOnHover(item.title.lang, alignment = pinned.side.asideAlignment)
            .onPlaced { anchor = it }
            .onPress { event ->
                dragging[0] = false
                if (event.isRightClick()) {
                    menu = true
                    event.consume()
                }
            }
            .onDrag { event ->
                if (event.isRightClick()) return@onDrag
                if (!dragging[0] && hypot(event.dragTotalX, event.dragTotalY) < DragThreshold) return@onDrag
                dragging[0] = true
                event.consume()

                if (abs(event.dragTotalX) > StripeButtonSize + UndockDistance) {
                    val spaceX = event.localXInAncestor(DockTags.Space) ?: event.rootLocalX
                    val spaceY = event.localYInAncestor(DockTags.Space) ?: event.rootLocalY
                    state.undockPinned(item.id, spaceX - event.localX, spaceY - event.localY, stripeButtonId(item.id))
                    return@onDrag
                }

                val height = stripeHeight()
                val along = event.localYInAncestor(stripeId(pinned.side)) ?: return@onDrag
                if (height <= 0f) return@onDrag
                val group = if (along < height / 2f) DockStripeGroup.TOP else DockStripeGroup.BOTTOM
                val target = DockAnchor(pinned.side, group)
                val others = state.pinnedIn(target).count { it.item.id != item.id }
                val index = if (group == DockStripeGroup.TOP) {
                    (along / StripeButtonPitch).toInt()
                } else {
                    others - ((height - along) / StripeButtonPitch).toInt()
                }
                state.movePinned(item.id, target, index.coerceIn(0, others))
            }
            .onRelease { event ->
                event.consume()
                if (!dragging[0] && !event.isRightClick()) state.togglePinned(item.id)
                dragging[0] = false
            },
    ) {
        item.icon?.let { icon ->
            Image(icon, tags = listOf(DockTags.StripeIcon), modifier = Modifier.align(UiAlign.CENTER, UiAlign.CENTER))
        }
    }

    if (menu) {
        ContextMenu(
            id = "dock-stripe-menu-${item.id}",
            anchorBounds = anchor,
            items = stripeMenuItems(pinned, state),
            alignment = pinned.side.asideAlignment,
            onExpandedChange = { menu = it },
        )
    }
}

private fun stripeMenuItems(pinned: DockPinnedItem, state: DockingState): List<UiDropdownItem> {
    val itemId = pinned.item.id
    val otherGroup = if (pinned.group == DockStripeGroup.TOP) DockStripeGroup.BOTTOM else DockStripeGroup.TOP
    return buildList {
        add(UiDropdownItem(label = DockLang.moveTo(otherGroup)) { state.pin(itemId, pinned.side, otherGroup) })
        add(UiDropdownItem(label = DockLang.pinTo(pinned.side.opposite)) { state.pin(itemId, pinned.side.opposite, pinned.group) })
        add(UiDropdownItem(label = DockLang.Undock) { state.unpin(itemId) })
        if (pinned.item.closable) {
            add(UiDropdownItem(label = DockLang.Close) { state.close(itemId) })
        }
        add(UiDropdownItem(label = DockLang.HideStripes, separatorBefore = true) { state.stripesVisible = false })
    }
}

/**
 * The side panel of [side]: what the top half of its stripe has open, full height of the space
 * above the bottom area, sliding the editor aside as it opens.
 */
@Composable
internal fun DockSidePanel(
    side: DockSide,
    state: DockingState,
    tabBarActions: DockTabBarActions,
    content: DockItemContent,
) {
    val target = state.expandedOn(side)
    var shown by remember { mutableStateOf(target) }
    SideEffect { if (target != null) shown = target }

    var opened by remember { mutableStateOf(false) }
    var sliding by remember { mutableStateOf(false) }
    LaunchedEffect(target == null) {
        if (target != null) {
            if (opened) return@LaunchedEffect
            sliding = true
            withFrameNanos { }
            opened = true
            delay(PanelSlideMillis.milliseconds)
            sliding = false
            return@LaunchedEffect
        }
        sliding = true
        delay(PanelSlideMillis.milliseconds)
        shown = null
        opened = false
        sliding = false
    }

    val panel = shown ?: return
    val width = if (target == null || !opened) 0f else panel.width
    val open = target != null && opened

    if (side == DockSide.RIGHT) DockPanelSplitter(panel, side, state, open, sliding)
    Column(
        id = "dock-pinned-${side.tag}",
        tags = buildList {
            add(DockTags.PinnedPanel)
            add(side.tag)
            if (sliding) add(DockTags.Sliding)
        },
        modifier = Modifier.size(width.px, 100.percent).clip(),
    ) {
        DockPinnedBody(panel, state, tabBarActions, content)
    }
    if (side == DockSide.LEFT) DockPanelSplitter(panel, side, state, open, sliding)
}

/**
 * The area the bottom halves of both stripes.
 */
@Composable
internal fun DockBottomArea(state: DockingState, tabBarActions: DockTabBarActions, content: DockItemContent) {
    val left = state.expandedIn(DockAnchor(DockSide.LEFT, DockStripeGroup.BOTTOM))
    val right = state.expandedIn(DockAnchor(DockSide.RIGHT, DockStripeGroup.BOTTOM))
    if (left == null && right == null) return

    val heightAtPress = remember { floatArrayOf(state.bottomHeight) }
    Box(
        id = "dock-bottom-splitter",
        tags = listOf(DockTags.Splitter),
        modifier = Modifier.size(100.percent, PanelSplitterWidth.px)
            .input(hoverable = true, draggable = true).cursor(UiCursorShape.RESIZE_VERTICAL)
            .onPress { heightAtPress[0] = state.bottomHeight }
            .onDrag { event ->
                state.setBottomHeight(heightAtPress[0] - event.dragTotalY)
                event.consume()
            },
    )
    Row(
        id = "dock-bottom-area",
        tags = listOf(DockTags.PinnedPanel, "bottom"),
        modifier = Modifier.size(100.percent, state.bottomHeight.px).clip(),
    ) {
        val both = left != null && right != null
        left?.let { panel ->
            Column(
                id = "dock-bottom-left",
                modifier = Modifier.size(0.px, 100.percent).grow(if (both) state.bottomFraction else 1f).clip(),
            ) { DockPinnedBody(panel, state, tabBarActions, content) }
        }
        if (both) DockBottomDivider(state)
        right?.let { panel ->
            Column(
                id = "dock-bottom-right",
                modifier = Modifier.size(0.px, 100.percent).grow(if (both) 1f - state.bottomFraction else 1f).clip(),
            ) { DockPinnedBody(panel, state, tabBarActions, content) }
        }
    }
}

@Composable
private fun DockBottomDivider(state: DockingState) {
    val fractionAtPress = remember { floatArrayOf(state.bottomFraction) }
    Box(
        id = "dock-bottom-divider",
        tags = listOf(DockTags.Splitter),
        modifier = Modifier.size(PanelSplitterWidth.px, 100.percent)
            .input(hoverable = true, draggable = true).cursor(UiCursorShape.RESIZE_HORIZONTAL)
            .onPress { fractionAtPress[0] = state.bottomFraction }
            .onDrag { event ->
                val width = event.parentWidth - PanelSplitterWidth
                if (width > 0f) state.setBottomFraction(fractionAtPress[0] + event.dragTotalX / width)
                event.consume()
            },
    )
}

/** A parked window as it shows when open: its header, then its content. */
@Composable
private fun DockPinnedBody(
    panel: DockPinnedItem,
    state: DockingState,
    tabBarActions: DockTabBarActions,
    content: DockItemContent,
) {
    val item = panel.item
    if (!item.titleInToolbar) DockPanelHeader(panel, tabBarActions)
    Box(
        id = "$PinnedContentPrefix${item.id}-content",
        tags = listOf(DockTags.Content),
        modifier = Modifier.size(100.percent, 0.px).grow(1f).clip().input(hoverable = true, clickable = true)
            .onPress { state.expand(item.id) },
    ) {
        key(item.id) {
            val title = if (item.titleInToolbar) DockPanelTitle(item.title.lang, item.icon) else null
            CompositionLocalProvider(LocalDockPanelTitle provides title) {
                DockContentBody { content(item) }
            }
        }
    }
}

@Composable
private fun DockPanelHeader(panel: DockPinnedItem, tabBarActions: DockTabBarActions) {
    val item = panel.item
    Row(
        id = "dock-pinned-header-${item.id}",
        tags = listOf(DockTags.PinnedHeader),
        modifier = Modifier.size(100.percent, 24.px).alignItems(vertical = UiAlign.CENTER),
    ) {
        item.icon?.let { icon -> Image(icon, tags = listOf(DockTags.PinnedHeaderIcon)) }
        Box(modifier = Modifier.size(0.px, 100.percent).grow(1f).clip()) {
            Text(
                item.title.lang,
                tags = listOf(DockTags.PinnedHeaderLabel),
                modifier = Modifier.align(UiAlign.START, UiAlign.CENTER).textWrap(false),
            )
        }
        key(item.id) { tabBarActions(item) }
    }
}

@Composable
private fun DockPanelSplitter(
    panel: DockPinnedItem,
    side: DockSide,
    state: DockingState,
    open: Boolean,
    sliding: Boolean,
) {
    val start = remember(panel.item.id) { floatArrayOf(panel.width) }
    Box(
        id = "dock-pinned-splitter-${side.tag}",
        tags = buildList {
            add(DockTags.Splitter)
            if (sliding) add(DockTags.Sliding)
        },
        modifier = Modifier.size(if (open) PanelSplitterWidth.px else 0.px, 100.percent)
            .input(hoverable = true, draggable = true).cursor(UiCursorShape.RESIZE_HORIZONTAL)
            .onPress { start[0] = panel.width }.onDrag { event ->
                val delta = if (side == DockSide.LEFT) event.dragTotalX else -event.dragTotalX
                state.setPinnedWidth(panel.item.id, start[0] + delta)
                event.consume()
            },
    )
}

internal val DockSide.tag: String get() = if (this == DockSide.LEFT) "left" else "right"

private val DockSide.asideAlignment: UiPopupAlignment
    get() = if (this == DockSide.LEFT) {
        UiPopupAlignment(
            anchorHorizontal = UiAlign.END,
            anchorVertical = UiAlign.CENTER,
            popupVertical = UiAlign.CENTER,
            offsetX = 6f,
        )
    } else {
        UiPopupAlignment(
            anchorHorizontal = UiAlign.START,
            anchorVertical = UiAlign.CENTER,
            popupHorizontal = UiAlign.END,
            popupVertical = UiAlign.CENTER,
            offsetX = -6f,
        )
    }

internal object DockLang {
    private const val Root = "hollowengine.gui.docking"

    val PinLeft: String get() = "$Root.pin_left".lang
    val PinRight: String get() = "$Root.pin_right".lang
    val PinLeftBottom: String get() = "$Root.pin_left_bottom".lang
    val PinRightBottom: String get() = "$Root.pin_right_bottom".lang
    val Undock: String get() = "$Root.undock".lang
    val Close: String get() = "$Root.close".lang
    val HideStripes: String get() = "$Root.hide_stripes".lang
    val ShowStripes: String get() = "$Root.show_stripes".lang
    val ParkHere: String get() = "$Root.park_here".lang

    fun pinTo(side: DockSide): String = if (side == DockSide.LEFT) PinLeft else PinRight

    fun moveTo(group: DockStripeGroup): String =
        if (group == DockStripeGroup.TOP) "$Root.move_to_top".lang else "$Root.move_to_bottom".lang
}
