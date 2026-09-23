package ru.hollowhorizon.hollowengine.client.ui.ide.timeline.ui

import androidx.compose.runtime.Composable
import ru.hollowhorizon.hollowengine.client.ui.inspector.*
import ru.hollowhorizon.hollowengine.client.ui.*
import ru.hollowhorizon.hollowengine.client.ui.ide.timeline.*
import ru.hollowhorizon.hollowengine.client.utils.lang

@Composable
internal fun PropertySettingsDialog(
    controller: TimelineController,
    property: AnimProperty<*>,
    refresh: () -> Unit,
    onClose: () -> Unit,
) {
    DialogFrame(CutsceneLang.PROPERTY_SETTINGS.lang, onClose) {
        Text(property.nameState, modifier = Modifier.fontSize(11f).foreground(TimelineColors.Text))

        RotationModeRow(property, controller, refresh)

        val bounded = property.channels.indices.filter { property.bounds(it) != ChannelBounds.Unbounded }
        if (bounded.isNotEmpty()) {
            Text(CutsceneLang.LIMITS.lang, modifier = Modifier.fontSize(9f).foreground(TimelineColors.Muted))
            Text(
                CutsceneLang.LIMITS_HINT.lang,
                modifier = Modifier.size(100.percent, UiLength.Fit).fontSize(9f).foreground(TimelineColors.Muted),
            )
            bounded.forEach { index ->
                Row(
                    modifier = Modifier.size(100.percent, UiLength.Fit).gap(6.px).alignItems(vertical = UiAlign.CENTER),
                ) {
                    Text(
                        property.channels[index].name,
                        modifier = Modifier.fontSize(10f).foreground(TimelineColors.Muted).textWrap(false),
                    )
                    Text(
                        boundsLabel(property.bounds(index)),
                        modifier = Modifier.grow(1f).fontSize(10f).foreground(TimelineColors.Text).textWrap(false),
                    )
                }
            }
        }

        Row(modifier = Modifier.size(100.percent, UiLength.Auto).align(horizontal = UiAlign.END)) {
            ToolbarButton(CutsceneLang.CLOSE.lang, "property-close", TimelineColors.Blue) { onClose() }
        }
    }
}

private fun boundsLabel(bounds: ChannelBounds): String {
    val low = bounds.minimum?.let { formatBound(it) }
    val high = bounds.maximum?.let { formatBound(it) }
    return when {
        low != null && high != null -> "$low .. $high"
        low != null -> ">= $low"
        high != null -> "<= $high"
        else -> ""
    }
}

private fun formatBound(value: Float): String =
    if (value == value.toInt().toFloat()) value.toInt().toString() else "%.2f".format(value).replace(',', '.')

@Composable
private fun RotationModeRow(property: AnimProperty<*>, controller: TimelineController, refresh: () -> Unit) {
    val type = property.type as? RotationPropertyType ?: return
    Text(CutsceneLang.ROTATION_MODE.lang, modifier = Modifier.fontSize(9f).foreground(TimelineColors.Muted))
    PillFlow(id = "property-rotation-modes") {
        RotationMode.entries.forEach { mode ->
            Pill(rotationModeLabel(mode), type.mode == mode, id = "rotation-mode-${mode.name}") {
                controller.edit("Change rotation basis") { property.setRotationMode(mode) }
                refresh()
            }
        }
    }
    Text(
        CutsceneLang.ROTATION_MODE_HINT.lang,
        modifier = Modifier.size(100.percent, UiLength.Fit).fontSize(9f).foreground(TimelineColors.Muted),
    )
}
