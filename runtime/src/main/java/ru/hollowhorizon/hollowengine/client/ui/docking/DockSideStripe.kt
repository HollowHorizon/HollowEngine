package ru.hollowhorizon.hollowengine.client.ui.docking

import androidx.compose.runtime.*
import kotlinx.coroutines.delay
import ru.hollowhorizon.hollowengine.client.ui.*
import ru.hollowhorizon.hollowengine.client.ui.layout.UiRect
import ru.hollowhorizon.hollowengine.client.ui.widgets.ContextMenu
import ru.hollowhorizon.hollowengine.client.ui.widgets.UiDropdownItem
import ru.hollowhorizon.hollowengine.client.ui.widgets.tooltipOnHover
import ru.hollowhorizon.hollowengine.client.utils.lang
import kotlin.time.Duration.Companion.milliseconds

/** Edge of a stripe button. */
private const val StripeButtonSize = 22f

/** How long a panel takes to push the editor aside, and to give the room back. */
private const val PanelSlideMillis = 160L

/** Width of the handle that drags a pinned panel's inner edge. */
private const val PanelSplitterWidth = 3f

internal fun DockingState.edgeWidth(side: DockSide): Float {
    val stripe = if (stripesVisible && pinnedOn(side).isNotEmpty()) StripeButtonSize else 0f
    val panel = expandedOn(side)?.let { it.width + PanelSplitterWidth } ?: 0f
    return stripe + panel
}

/**
 * One edge of the dock space: the stripe of buttons for the tool windows parked on [side], and the
 * panel the open one expands into.
 */
@Composable
internal fun DockSideEdge(
    side: DockSide,
    state: DockingState,
    tabBarActions: DockTabBarActions,
    content: DockItemContent,
) {
    if (side == DockSide.LEFT) {
        DockStripe(side, state)
        DockPinnedPanel(side, state, tabBarActions, content)
    } else {
        DockPinnedPanel(side, state, tabBarActions, content)
        DockStripe(side, state)
    }
}

@Composable
private fun DockStripe(side: DockSide, state: DockingState) {
    val items = state.pinnedOn(side)
    val open = state.stripesVisible && items.isNotEmpty()
    Column(
        id = "dock-stripe-${side.tag}",
        tags = listOf(DockTags.Stripe, side.tag),
        modifier = Modifier.size(if (open) StripeButtonSize.px else 0.px, 100.percent)
            .alignItems(horizontal = UiAlign.CENTER).clip(),
    ) {
        items.forEach { pinned ->
            key(pinned.item.id) { DockStripeButton(pinned, state) }
        }
    }
}

@Composable
private fun DockStripeButton(pinned: DockPinnedItem, state: DockingState) {
    val item = pinned.item
    val expanded = state.expandedOn(pinned.side)?.item?.id == item.id
    var anchor by remember { mutableStateOf(UiRect.Zero) }
    var menu by remember { mutableStateOf(false) }

    Box(
        id = "dock-stripe-button-${item.id}",
        tags = buildList {
            add(DockTags.StripeButton)
            if (expanded) add(DockTags.Selected)
        },
        modifier = Modifier.size(StripeButtonSize.px, StripeButtonSize.px).input(hoverable = true, clickable = true)
            .cursor(UiCursorShape.HAND).tooltipOnHover(item.title.lang, alignment = pinned.side.asideAlignment)
            .onPlaced { anchor = it }.onPress { event ->
                if (event.isRightClick()) {
                    menu = true
                    event.consume()
                }
            }.onClick { event ->
                if (!event.isRightClick()) state.togglePinned(item.id)
                event.consume()
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
    return buildList {
        add(UiDropdownItem(label = DockLang.pinTo(pinned.side.opposite)) { state.pin(itemId, pinned.side.opposite) })
        add(UiDropdownItem(label = DockLang.Undock) { state.unpin(itemId) })
        if (pinned.item.closable) {
            add(UiDropdownItem(label = DockLang.Close) { state.close(itemId) })
        }
        add(UiDropdownItem(label = DockLang.HideStripes, separatorBefore = true) { state.stripesVisible = false })
    }
}

@Composable
private fun DockPinnedPanel(
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
        DockPanelHeader(panel, tabBarActions)
        Box(
            id = "dock-pinned-content-${panel.item.id}",
            tags = listOf(DockTags.Content),
            modifier = Modifier.size(100.percent, 0.px).grow(1f).clip().input(hoverable = true, clickable = true)
                .onPress { state.expand(panel.item.id) },
        ) {
            key(panel.item.id) {
                DockContentBody { content(panel.item) }
            }
        }
    }
    if (side == DockSide.LEFT) DockPanelSplitter(panel, side, state, open, sliding)
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

private val DockSide.tag: String get() = if (this == DockSide.LEFT) "left" else "right"

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
    val Undock: String get() = "$Root.undock".lang
    val Close: String get() = "$Root.close".lang
    val HideStripes: String get() = "$Root.hide_stripes".lang
    val ShowStripes: String get() = "$Root.show_stripes".lang

    fun pinTo(side: DockSide): String = if (side == DockSide.LEFT) PinLeft else PinRight
}
