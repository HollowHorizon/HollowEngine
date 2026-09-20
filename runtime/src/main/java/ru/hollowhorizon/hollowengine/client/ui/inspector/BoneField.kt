package ru.hollowhorizon.hollowengine.client.ui.inspector

import androidx.compose.runtime.*
import ru.hollowhorizon.hollowengine.client.ui.*
import ru.hollowhorizon.hollowengine.client.ui.layout.UiRect
import ru.hollowhorizon.hollowengine.client.utils.lang

val LocalEditorBones = staticCompositionLocalOf { emptyList<String>() }

@Composable
internal fun BoneField(label: String?, description: String?, value: String, onChange: (String) -> Unit) {
    val bones = LocalEditorBones.current
    var open by remember { mutableStateOf(false) }
    var anchor by remember { mutableStateOf(UiRect.Zero) }

    Column(tags = listOf("insp-field")) {
        FieldLabel(label, description)
        Row(
            tags = listOf("insp-bone", if (value.isBlank()) "empty" else "set"),
            modifier = Modifier.size(100.percent, 22.px)
                .input(hoverable = true, clickable = true)
                .cursor(UiCursorShape.HAND)
                .alignItems(vertical = UiAlign.CENTER)
                .onPlaced { anchor = it }
                .onClick { event ->
                    open = !open
                    event.consume()
                },
        ) {
            Text(
                value.ifBlank { boneText("none") },
                tags = listOf("insp-bone-label"),
                modifier = Modifier.grow(1f),
            )
            Box(tags = listOf("dropdown-button-arrow"))
        }
    }

    if (open) BonePopup(anchor, bones, value, onDismiss = { open = false }) { picked ->
        open = false
        onChange(picked)
    }
}

@Composable
private fun BonePopup(
    anchor: UiRect,
    bones: List<String>,
    selected: String,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val matches = remember(bones, query) {
        if (query.isBlank()) bones else bones.filter { it.contains(query, ignoreCase = true) }
    }

    Popup(
        anchorBounds = anchor,
        id = "insp-bone-popup",
        tags = listOf("dropdown-popup", "insp-bone-popup"),
        onDismiss = onDismiss,
    ) {
        TextField(
            value = query,
            id = "insp-bone-search",
            placeholder = boneText("search"),
            fontSize = 9f,
            onChange = { query = it },
            tags = listOf("insp-input"),
            modifier = Modifier.size(100.percent, 20.px),
        )

        Column(
            tags = listOf("insp-bone-list"),
            modifier = Modifier.scrollable(horizontal = false),
        ) {
            if (selected.isNotBlank()) BoneRow(boneText("clear"), selected = false) { onPick("") }
            matches.forEach { bone ->
                key(bone) { BoneRow(bone, selected = bone == selected) { onPick(bone) } }
            }
            if (matches.isEmpty()) Text(boneText("no_matches"), tags = listOf("insp-hint"))
        }
    }
}

@Composable
private fun BoneRow(label: String, selected: Boolean, onPick: () -> Unit) {
    Row(
        tags = if (selected) listOf("dropdown-item", "selected") else listOf("dropdown-item"),
        modifier = Modifier.input(hoverable = true, clickable = true)
            .cursor(UiCursorShape.HAND)
            .alignItems(vertical = UiAlign.CENTER)
            .onClick { event ->
                onPick()
                event.consume()
            },
    ) {
        Text(label, tags = listOf("dropdown-item-label"))
    }
}

private fun boneText(name: String): String = "hollowengine.gui.inspector.bone.$name".lang
