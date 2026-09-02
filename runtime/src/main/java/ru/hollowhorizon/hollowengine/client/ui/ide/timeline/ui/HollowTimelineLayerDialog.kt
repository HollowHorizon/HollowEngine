package ru.hollowhorizon.hollowengine.client.ui.ide.timeline.ui

import androidx.compose.runtime.Composable
import ru.hollowhorizon.hollowengine.client.ui.*
import ru.hollowhorizon.hollowengine.client.ui.ide.timeline.*
import ru.hollowhorizon.hollowengine.client.utils.lang

@Composable
internal fun LayerSettingsDialog(
    controller: TimelineController,
    layer: AnimLayer,
    refresh: () -> Unit,
    onClose: () -> Unit,
) {
    val property = controller.propertyOf(layer) ?: return onClose()
    DialogFrame(CutsceneLang.LAYER_SETTINGS.lang, onClose) {
        Text(property.nameState, modifier = Modifier.fontSize(10f).foreground(TimelineColors.Muted))
        TextField(
            value = layer.nameState,
            onChange = { layer.nameState = it; refresh() },
            modifier = Modifier.size(100.percent, 24.px).background(TimelineColors.Background)
                .border(1.px, TimelineColors.Border, 3f).padding(6.px, 2.px).foreground(TimelineColors.Text)
                .fontSize(11f),
        )

        Text(CutsceneLang.BLEND_MODE.lang, modifier = Modifier.fontSize(9f).foreground(TimelineColors.Muted))
        PillFlow(id = "layer-blend-modes") {
            val modes = if (property.layers.firstOrNull() === layer) setOf(layer.blendMode)
            else property.type.blendModes
            modes.forEach { mode ->
                Pill("layer-blend-${mode.name}", blendModeLabel(mode), layer.blendMode == mode) {
                    controller.edit("Edit layer blending") { layer.blendMode = mode }
                    refresh()
                }
            }
        }

        if (property.channels.any { it.supportsCurveEditor }) {
            FloatField(CutsceneLang.LAYER_WEIGHT.lang, layer.weight, 0f, 1f) { next ->
                controller.edit("Edit layer weight") { layer.weight = next }
                refresh()
            }
        }

        Row(modifier = Modifier.size(100.percent, 24.px).alignItems(vertical = UiAlign.CENTER).gap(6.px)) {
            TogglePill(CutsceneLang.LAYER_VISIBLE.lang, layer.isVisible) {
                layer.isVisible = !layer.isVisible
                refresh()
            }
            TogglePill(CutsceneLang.LAYER_LOCKED.lang, layer.isLocked) {
                layer.isLocked = !layer.isLocked
                refresh()
            }
        }

        Row(modifier = Modifier.size(100.percent, UiLength.Auto).gap(8.px).align(horizontal = UiAlign.END)) {
            if (property.layers.size > 1) {
                ToolbarButton(CutsceneLang.LAYER_DELETE.lang, "layer-delete", TimelineColors.Danger) {
                    controller.edit("Delete layer") {
                        property.layers.remove(layer)
                        if (controller.activeLayer === layer) controller.activeLayer = property.layers.firstOrNull()
                    }
                    refresh()
                    onClose()
                }
            }
            ToolbarButton(CutsceneLang.CLOSE.lang, "layer-close", TimelineColors.Blue) { onClose() }
        }
    }
}

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
            Pill("rotation-mode-${mode.name}", rotationModeLabel(mode), type.mode == mode) {
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
