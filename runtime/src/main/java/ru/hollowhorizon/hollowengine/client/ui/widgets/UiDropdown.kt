package ru.hollowhorizon.hollowengine.client.ui.widgets

import androidx.compose.runtime.Composable
import ru.hollowhorizon.hollowengine.client.ui.Box
import ru.hollowhorizon.hollowengine.client.ui.Checkbox
import ru.hollowhorizon.hollowengine.client.ui.Image
import ru.hollowhorizon.hollowengine.client.ui.Modifier
import ru.hollowhorizon.hollowengine.client.ui.Popup
import ru.hollowhorizon.hollowengine.client.ui.Row
import ru.hollowhorizon.hollowengine.client.ui.Text
import ru.hollowhorizon.hollowengine.client.ui.UiAlign
import ru.hollowhorizon.hollowengine.client.ui.UiCheckboxVariant
import ru.hollowhorizon.hollowengine.client.ui.UiCursorShape
import ru.hollowhorizon.hollowengine.client.ui.UiEvent
import ru.hollowhorizon.hollowengine.client.ui.UiPopupAnchor
import ru.hollowhorizon.hollowengine.client.ui.px

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
    Row(
        id = id,
        tags = listOf("dropdown-button") + tags,
        modifier = Modifier.then(
            Modifier.input(hoverable = true, clickable = true),
            Modifier.cursor(UiCursorShape.HAND),
            Modifier.alignItems(vertical = UiAlign.CENTER),
            Modifier.onClick { event ->
                onExpandedChange(!expanded)
                event.consume()
            },
        ),
    ) {
        if (icon != null) Image(icon, tags = listOf("dropdown-button-icon"))
        Text(label, tags = listOf("dropdown-button-label"))
        Box(tags = listOf("dropdown-button-arrow"))
    }

    if (!expanded) return
    Popup(
        anchor = UiPopupAnchor.Node(id),
        id = "$id-popup",
        tags = listOf("dropdown-popup"),
    ) {
        items.forEachIndexed { index, item ->
            val itemAction: (UiEvent) -> Unit = { event ->
                if (item.enabled) {
                    if (item.closeOnClick) onExpandedChange(false)
                    item.onClick()
                    event.consume()
                }
            }
            Row(
                id = "$id-item-$index",
                tags = if (item.enabled) listOf("dropdown-item") else listOf("dropdown-item", "disabled"),
                modifier = Modifier.then(
                    Modifier.input(hoverable = item.enabled, clickable = item.enabled),
                    Modifier.cursor(if (item.enabled) UiCursorShape.HAND else UiCursorShape.DEFAULT),
                    Modifier.alignItems(vertical = UiAlign.CENTER),
                    Modifier.onClick(itemAction),
                ),
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
