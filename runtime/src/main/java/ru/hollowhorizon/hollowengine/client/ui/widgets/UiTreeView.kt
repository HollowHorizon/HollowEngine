package ru.hollowhorizon.hollowengine.client.ui.widgets

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import ru.hollowhorizon.hollowengine.client.ui.*
import ru.hollowhorizon.hollowengine.client.ui.scroll.rememberScrollState

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
) {
    val scroll = rememberScrollState()
    val dragAndDrop = LocalDragAndDrop.current
    Column(
        tags = listOf("tree-view") + tags,
        modifier = modifier.scrollable(state = scroll)
    ) {
        items.forEach { item ->
            key(item.id) {
                UiTreeRow(item, onToggle, onSelect, onIconClick, fillRowWidth, onDrop) {
                    if (dragItem == null) null else dragAndDrop?.let { state -> state to dragItem(item) }
                }
            }
        }
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
    drag: () -> Pair<UiDragAndDropState, UiDragItem?>?,
) {
    val dropModifier = if (onDrop == null) Modifier else Modifier.dropTarget(
        onDrop = { dragged, _, _ -> onDrop(item, dragged) },
    )
    Row(
        id = "tree-item-${item.id}",
        tags = if (item.selected) listOf("tree-item", "selected") else listOf("tree-item"),
        modifier = Modifier.size(if (fillRowWidth) 100.percent else UiLength.Auto, 24.px)
            .alignItems(vertical = UiAlign.CENTER)
            .input(hoverable = true, clickable = true)
            .cursor(UiCursorShape.HAND)
            .onClick { event ->
                onSelect(item, event)
                event.consume()
            }
            .dragSource(drag()?.first) { drag()?.second }
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
