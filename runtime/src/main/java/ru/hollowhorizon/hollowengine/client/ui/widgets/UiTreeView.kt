package ru.hollowhorizon.hollowengine.client.ui.widgets

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import ru.hollowhorizon.hollowengine.client.ui.*

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
) {
    LazyColumn(
        tags = listOf("tree-view") + tags,
        modifier = modifier.scroll(vertical = true, horizontal = true)
    ) {
        items.forEach { item ->
            key(item) {
                UiTreeRow(item, onToggle, onSelect)
            }
        }
    }
}

@Composable
private fun <T> UiTreeRow(
    item: UiTreeItem<T>,
    onToggle: (UiTreeItem<T>) -> Unit,
    onSelect: (UiTreeItem<T>, UiEvent) -> Unit,
) {
    Row(
        id = "tree-item-${item.id}",
        tags = if (item.selected) listOf("tree-item", "selected") else listOf("tree-item"),
        modifier = Modifier.size(100.percent, 24.px)
            .alignItems(vertical = UiAlign.CENTER)
            .input(hoverable = true, clickable = true)
            .cursor(UiCursorShape.HAND)
            .onClick { event ->
                onSelect(item, event)
                event.consume()
            }
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
        if (item.icon != null) Image(item.icon, tags = listOf("tree-icon"))
        Text(item.label, tags = listOf("tree-label"))
    }
}
