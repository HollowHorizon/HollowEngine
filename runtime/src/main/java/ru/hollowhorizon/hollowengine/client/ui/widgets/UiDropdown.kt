package ru.hollowhorizon.hollowengine.client.ui.widgets

import androidx.compose.runtime.*
import ru.hollowhorizon.hollowengine.client.ui.*
import ru.hollowhorizon.hollowengine.client.ui.layout.UiRect

enum class UiDropdownMark {
    CHECKBOX,
    RADIO
}

data class UiDropdownSlider(
    val value: Float,
    val min: Float = 0f,
    val max: Float = 1f,
    val step: Float = 0f,
    val valueLabel: (Float) -> String = { it.toString() },
    val onChange: ((Float) -> Unit)? = null,
    val onCommit: ((Float) -> Unit)? = null,
)

data class UiDropdownItem(
    val label: String,
    val icon: String? = null,
    val enabled: Boolean = true,
    val checked: Boolean = false,
    val mark: UiDropdownMark? = null,
    val slider: UiDropdownSlider? = null,
    val closeOnClick: Boolean = true,
    val onClick: () -> Unit = {},
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
    ContextMenu(id, anchorBounds, items, onExpandedChange)
}

@Composable
fun ContextMenu(
    id: String,
    anchorBounds: UiRect,
    items: List<UiDropdownItem>,
    onExpandedChange: (Boolean) -> Unit = {},
) {
    Popup(
        anchorBounds = anchorBounds,
        id = "$id-popup",
        tags = listOf("dropdown-popup"),
        onDismiss = { onExpandedChange(false) },
    ) {
        items.forEachIndexed { index, item ->
            if (item.slider != null) {
                DropdownSliderRow("$id-item-$index", item.label, item.slider)
                return@forEachIndexed
            }
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

@Composable
private fun DropdownSliderRow(id: String, label: String, slider: UiDropdownSlider) {
    var live by remember(slider.value) { mutableStateOf(slider.value) }
    Row(
        id = id,
        tags = listOf("dropdown-item", "dropdown-slider-item"),
        modifier = Modifier.alignItems(vertical = UiAlign.CENTER),
    ) {
        Text(label, tags = listOf("dropdown-item-label"))
        Slider(
            value = slider.value,
            min = slider.min,
            max = slider.max,
            step = slider.step,
            onValueChange = { live = it; slider.onChange?.invoke(it) },
            onValueCommit = { slider.onCommit?.invoke(it) },
            tags = listOf("dropdown-item-slider"),
            modifier = Modifier.grow(),
        )
        Text(slider.valueLabel(live), tags = listOf("dropdown-item-value"))
    }
}