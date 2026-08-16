package ru.hollowhorizon.hollowengine.client.ui.widgets

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import ru.hollowhorizon.hollowengine.client.ui.*
import ru.hollowhorizon.hollowengine.client.ui.layout.UiRect
import kotlin.time.Duration.Companion.milliseconds

/**
 * A hover tooltip for the node the returned modifier is applied to.
 *
 * It is a modifier rather than a wrapper composable so it can be added to a button that already has
 * its own layout, without a container in between: the call sites the tooltip and the popup it emits
 * both live here, and the caller only spends a `.then(...)`.
 *
 * ```kotlin
 * Box(modifier = Modifier.onClick { ... }.tooltipOnHover("Next (Enter)"))
 * ```
 */
@Composable
fun Modifier.tooltipOnHover(
    text: String,
    alignment: UiPopupAlignment = UiPopupAlignment.BelowStart,
    delayMillis: Long = TooltipDelayMillis,
): Modifier {
    var bounds by remember { mutableStateOf(UiRect.Zero) }
    var hovered by remember { mutableStateOf(false) }
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(hovered, text, delayMillis) {
        if (!hovered || text.isBlank()) {
            visible = false
            return@LaunchedEffect
        }
        delay(delayMillis.milliseconds)
        visible = true
    }

    if (visible && bounds.width > 0f) {
        Popup(
            anchorBounds = bounds,
            alignment = alignment,
            layer = TooltipLayer,
            tags = listOf("ui-tooltip"),
            modifier = Modifier.inputTransparent(),
            dismissOnOutside = false,
        ) {
            Text(text, tags = listOf("ui-tooltip-label"))
        }
    }

    return then(Modifier.input(hoverable = true)
        .onEnter { hovered = true }
        .onExit { hovered = false }
        .onPlaced { bounds = it })
}

private const val TooltipDelayMillis = 450L
private const val TooltipLayer = 100
