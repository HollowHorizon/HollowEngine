package ru.hollowhorizon.hollowengine.client.ui.widgets

import androidx.compose.runtime.*
import org.lwjgl.glfw.GLFW
import ru.hollowhorizon.hollowengine.client.ui.*
import ru.hollowhorizon.hollowengine.client.ui.scroll.UiScrollHandle
import ru.hollowhorizon.hollowengine.client.ui.scroll.rememberScrollState

private const val SearchIcon = "hollowengine:textures/gui/icons/search.svg"
private const val CloseIcon = "hollowengine:textures/gui/icons/cross.svg"

@Stable
class UiTreeFilterState(val inputId: String) {
    var query by mutableStateOf("")
    var expanded by mutableStateOf(false)
        private set

    fun open() {
        expanded = true
    }

    fun close() {
        query = ""
        expanded = false
    }
}

data class UiTreeItem<T>(
    val id: String,
    val label: String,
    val depth: Int,
    val payload: T,
    val icon: String? = null,
    val hasChildren: Boolean = false,
    val expanded: Boolean = false,
    val selected: Boolean = false,
)

@Composable
fun <T> UiTreeView(
    items: List<UiTreeItem<T>>,
    onToggle: (UiTreeItem<T>) -> Unit,
    onSelect: (UiTreeItem<T>, UiEvent) -> Unit,
    modifier: Modifier = Modifier.size(100.percent, 100.percent),
    tags: Iterable<String> = emptyList(),
    onIconClick: ((UiTreeItem<T>) -> Unit)? = null,
    fillRowWidth: Boolean = true,
    dragItem: ((UiTreeItem<T>) -> UiDragItem?)? = null,
    onDrop: ((UiTreeItem<T>, UiDragItem) -> Boolean)? = null,
    canDrop: (UiTreeItem<T>, UiDragItem) -> Boolean = { _, _ -> true },
    filterState: UiTreeFilterState? = null,
    filterPlaceholder: String = "Filter",
    onFilterOpened: ((String) -> Unit)? = null,
    scrollState: UiScrollHandle = rememberScrollState(),
) {
    Column(
        tags = listOf("tree-view") + tags,
        modifier = modifier.focus().onKeyInput(FilterShortcutPriority) { input ->
            if (filterState == null || input.repeat || !input.command || input.key != GLFW.GLFW_KEY_F) {
                return@onKeyInput
            }
            filterState.open()
            input.consume()
        },
    ) {
        if (filterState?.expanded == true) {
            LaunchedEffect(filterState.inputId) {
                onFilterOpened?.invoke(filterState.inputId)
            }
            UiTreeFilter(
                state = filterState,
                placeholder = filterPlaceholder,
                onClose = filterState::close,
            )
        }
        Column(
            tags = listOf("tree-view-scroll"),
            modifier = Modifier.size(100.percent, 0.px).grow(1f).scrollable(state = scrollState),
        ) {
            items.forEach { item ->
                key(item.id) {
                    UiTreeRow(
                        item, onToggle, onSelect, onIconClick, fillRowWidth, onDrop, canDrop,
                        draggable = dragItem != null,
                    ) {
                        dragItem?.invoke(item)
                    }
                }
            }
        }
    }
}

@Composable
private fun UiTreeFilter(
    state: UiTreeFilterState,
    placeholder: String,
    onClose: () -> Unit,
) {
    Row(tags = listOf("tree-filter-row"), modifier = Modifier.dropTarget(accepts = { false }, onDrop = { _, _, _ -> false })) {
        Image(SearchIcon, tags = listOf("tree-filter-icon"))
        TextField(
            value = state.query,
            placeholder = placeholder,
            onChange = { state.query = it },
            id = state.inputId,
            tags = listOf("tree-filter-input"),
            modifier = Modifier.grow(1f).onKeyInput(FilterShortcutPriority) { input ->
                if (input.key != GLFW.GLFW_KEY_ESCAPE) return@onKeyInput
                onClose()
                input.consume()
            },
        )
        Image(
            CloseIcon,
            tags = listOf("tree-filter-close"),
            modifier = Modifier.cursor(UiCursorShape.HAND).onClick { event ->
                onClose()
                event.consume()
            },
        )
    }
}

@Composable
private fun <T> UiTreeRow(
    item: UiTreeItem<T>,
    onToggle: (UiTreeItem<T>) -> Unit,
    onSelect: (UiTreeItem<T>, UiEvent) -> Unit,
    onIconClick: ((UiTreeItem<T>) -> Unit)?,
    fillRowWidth: Boolean,
    onDrop: ((UiTreeItem<T>, UiDragItem) -> Boolean)?,
    canDrop: (UiTreeItem<T>, UiDragItem) -> Boolean,
    draggable: Boolean,
    drag: () -> UiDragItem?,
) {
    val id = "tree-item-${item.id}"
    val dragAndDrop = LocalDragAndDrop.current
    val dragSource = dragAndDrop.takeIf { draggable }
    val dropModifier = if (onDrop == null) Modifier else Modifier.dropTarget(
        id = id,
        accepts = { canDrop(item, it) },
        onDrop = { dragged, _, _ -> onDrop(item, dragged) },
    )
    val select: (UiEvent) -> Unit = { event ->
        onSelect(item, event)
        event.consume()
    }
    Row(
        id = id,
        tags = listOfNotNull("tree-item", "selected".takeIf { item.selected },
            if (dragAndDrop?.hoveredTargetId == id) {
                if (dragAndDrop.canDrop) "drop-target" else "drop-rejected"
            } else null),
        modifier = Modifier.size(if (fillRowWidth) 100.percent else UiLength.Auto, 24.px)
            .alignItems(vertical = UiAlign.CENTER)
            .input(hoverable = true, clickable = true)
            .cursor(UiCursorShape.HAND)
            .then(if (dragSource != null) Modifier.onPress { onSelect(item, it) } else Modifier.onClick(select))
            .dragSource(dragSource, drag)
            .then(dropModifier)
    ) {
        repeat(item.depth) {
            Box(tags = listOf("tree-indent"))
        }
        Box(
            tags = if (!item.hasChildren) listOf("tree-expander-empty") else listOf("tree-expander"),
            attributes = mapOf(
                "expanded" to if (item.expanded) "true" else "false",
            ),
            modifier = Modifier.onClick { event ->
                if (item.hasChildren) onToggle(item)
                event.consume()
            },
        )
        if (item.icon != null) {
            val iconModifier = if (onIconClick != null) {
                Modifier.input(hoverable = true, clickable = true)
                    .cursor(UiCursorShape.HAND)
                    .onClick { event ->
                        onIconClick(item)
                        event.consume()
                    }
            } else null
            Image(
                item.icon,
                tags = if (onIconClick != null) listOf("tree-icon", "tree-icon-button") else listOf("tree-icon"),
                modifier = iconModifier,
            )
        }
        Text(item.label, tags = listOf("tree-label"))
    }
}

private const val FilterShortcutPriority = 100
