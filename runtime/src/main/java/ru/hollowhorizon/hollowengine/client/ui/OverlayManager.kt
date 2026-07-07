package ru.hollowhorizon.hollowengine.client.ui

import androidx.compose.runtime.*

/**
 * One open overlay (popup) tracked by the [OverlayManager]. A [Popup] owns an entry while composed; the
 * [OverlayHost] reads [content] (and [layer] for ordering) to compose the popup out-of-line, on top of
 * everything. [key] is a stable identity so the host can `key()` each popup across list changes.
 */
class PopupEntry(val key: Any) {
    var layer by mutableStateOf(0)
    var dismissOnOutside by mutableStateOf(true)

    var onDismiss: (() -> Unit)? = null
    var content by mutableStateOf<@Composable () -> Unit>({})
}

/**
 * Tracks the open overlays (popups) for the surface's [OverlayHost], which composes them out-of-line
 * above the content. Overlays register while composed; the host shows a full-screen dismiss catcher
 * while any dismiss-on-outside overlay is open and closes them on an outside click or Escape.
 */
class OverlayManager {
    val popups = mutableStateListOf<PopupEntry>()

    val hasDismissable: Boolean get() = popups.any { it.dismissOnOutside && it.onDismiss != null }

    /** Registers an overlay; returns the de-registration handle (call on dispose). */
    fun register(entry: PopupEntry): () -> Unit {
        popups += entry
        return { popups -= entry }
    }

    fun dismissAll() {
        popups.toList().forEach { if (it.dismissOnOutside) it.onDismiss?.invoke() }
    }
}

val LocalOverlayManager = staticCompositionLocalOf<OverlayManager?> { null }
