package ru.hollowhorizon.hollowengine.client.ui.inspector

import androidx.compose.runtime.*
import ru.hollowhorizon.hollowengine.client.ui.inspector.InspectorSelection.current

/**
 * What the inspector is showing, wherever it is drawn.
 */
object InspectorSelection {
    var current: InspectorTarget? by mutableStateOf(null)
        private set

    /** Which editor put [current] there, so only that one can take it back. */
    private var owner: String? = null

    /**
     * Shows what [source] has selected. Last publisher wins, which is what makes the panel follow
     * the pointer from one editor to the next. Passing `null` means that editor has nothing
     * selected anymore.
     */
    fun publish(source: String, target: InspectorTarget?) {
        if (target == null) {
            release(source)
            return
        }
        if (owner == source && current?.id == target.id) return
        owner = source
        current = target
    }

    /** Forgets what [source] published, if it is still the one on show. */
    fun release(source: String) {
        if (owner != source) return
        owner = null
        current = null
    }
}

/**
 * Keeps [target] on show for as long as this editor is composed, and takes it back when it is not.
 * [key] is what makes it a different selection; the same key republishes nothing.
 */
@Composable
fun PublishInspector(source: String, key: Any?, target: () -> InspectorTarget?) {
    LaunchedEffect(source, key) { InspectorSelection.publish(source, target()) }
    DisposableEffect(source) { onDispose { InspectorSelection.release(source) } }
}
