package ru.hollowhorizon.hollowengine.client.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The stretch of time a UI host keeps drawing after something asked it to close, so the `:closing`
 * rules of its stylesheet have frames to play in.
 */
class UiExitWindow(private val durationMillis: Long) {
    private var armedAt by mutableStateOf(0L)
    private var drawnAt = 0L

    /** Whether the window is open, i.e. the host is playing itself out. */
    val isClosing: Boolean get() = armedAt != 0L

    /**
     * Opens the window, or keeps the one already open. Returns false when the host declares no exit
     * duration: there is nothing to wait for and the caller should close outright.
     */
    fun begin(nowMillis: Long = System.currentTimeMillis()): Boolean {
        if (durationMillis <= 0L) return false
        if (armedAt == 0L) armedAt = nowMillis
        return true
    }

    /** The host drew a closing frame. The first one starts the clock; the rest are ignored. */
    fun markDrawn(nowMillis: Long = System.currentTimeMillis()) {
        if (armedAt != 0L && drawnAt == 0L) drawnAt = nowMillis
    }

    /**
     * Whether an opened window has run out and the host may go.
     */
    fun isFinished(nowMillis: Long = System.currentTimeMillis()): Boolean {
        val startedAt = if (drawnAt != 0L) drawnAt else armedAt
        return armedAt != 0L && nowMillis - startedAt >= durationMillis
    }
}
