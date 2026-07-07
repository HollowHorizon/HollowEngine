package ru.hollowhorizon.hollowengine.client.ui.widgets

import androidx.compose.runtime.*
import ru.hollowhorizon.hollowengine.client.ui.*
import ru.hollowhorizon.hollowengine.client.ui.layout.UiRect

enum class UiDropdownMark {
    CHECKBOX,
    RADIO
}

data class UiDropdownItem(
    val label: String,
    val icon: String? = null,
    val enabled: Boolean = true,
    val checked: Boolean = false,
    val mark: UiDropdownMark? = null,
    val closeOnClick: Boolean = true,
    val onClick: () -> Unit,
)

@Composable
fun UiDropdown(
    id: String,
    label: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    items: List<UiDropdownItem>,
    icon: String? = null,
    tags: Iterable<String> = emptyList(),
) {
    var anchorBounds by remember { mutableStateOf(UiRect.Zero) }
    Row(
        id = id,
        tags = listOf("dropdown-button") + tags,
        modifier = Modifier.input(hoverable = true, clickable = true)
            .cursor(UiCursorShape.HAND)
            .alignItems(vertical = UiAlign.CENTER)
            .onPlaced { anchorBounds = it }
            .onClick { event ->
                onExpandedChange(!expanded)
                event.consume()
            }
    ) {
        if (icon != null) Image(icon, tags = listOf("dropdown-button-icon"))
        Text(label, tags = listOf("dropdown-button-label"))
        Box(tags = listOf("dropdown-button-arrow"))
    }

    if (!expanded) return
    Popup(
        anchorBounds = anchorBounds,
        id = "$id-popup",
        tags = listOf("dropdown-popup"),
        onDismiss = { onExpandedChange(false) },
    ) {
        items.forEachIndexed { index, item ->
            val itemAction: (UiEvent) -> Unit = { event ->
                if (item.enabled) {
                    if (item.closeOnClick) dismiss()
                    item.onClick()
                    event.consume()
                }
            }
            Row(
                id = "$id-item-$index",
                tags = if (item.enabled) listOf("dropdown-item") else listOf("dropdown-item", "disabled"),
                modifier = Modifier.input(hoverable = item.enabled, clickable = item.enabled)
                    .cursor(if (item.enabled) UiCursorShape.HAND else UiCursorShape.DEFAULT)
                    .alignItems(vertical = UiAlign.CENTER)
                    .onClick(itemAction)
            ) {
                if (item.mark != null) {
                    Checkbox(
                        checked = item.checked,
                        variant = when (item.mark) {
                            UiDropdownMark.CHECKBOX -> UiCheckboxVariant.CHECKBOX
                            UiDropdownMark.RADIO -> UiCheckboxVariant.RADIO
                        },
                        tags = listOf("dropdown-item-check"),
                        modifier = Modifier.onClick(itemAction),
                    )
                } else if (item.icon != null) {
                    Image(item.icon, tags = listOf("dropdown-item-icon"))
                } else {
                    Box(modifier = Modifier.size(16.px, 16.px))
                }
                Text(item.label, tags = listOf("dropdown-item-label"))
            }
        }
    }
}
