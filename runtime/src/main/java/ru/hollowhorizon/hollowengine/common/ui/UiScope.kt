package ru.hollowhorizon.hollowengine.common.ui

import net.minecraft.nbt.CompoundTag

/**
 * The receiver a UI script's `content { }` block composes against. It carries everything a piece of
 * scripted UI needs from its host: the bound document, a channel back to the server, and a way to
 * dismiss itself.
 */
interface UiScope {
    /** The document this UI is bound to; server patches land here. */
    val data: UiData

    /**
     * The server session driving this UI, or null for a purely client-side overlay. Content that talks
     * to per-session server state, such as slots, needs it to know which session it belongs to.
     */
    val sessionId: Int?

    /** True while a server session backs this UI; false for a purely client-side overlay. */
    val isServerBound: Boolean get() = sessionId != null

    /**
     * True while the UI is playing itself out after something asked it to close, which is the window
     * an exit animation has. Content that wants one reads this and puts its root in
     * [ru.hollowhorizon.hollowengine.client.ui.UiState.CLOSING], the state its stylesheet selects on.
     */
    val isClosing: Boolean get() = false

    /** Sends a payload to the server session that opened this UI. No-op when not server-bound. */
    fun send(payload: CompoundTag)

    /** Closes this screen, or hides this overlay. */
    fun close()
}

/** Builds and sends a payload to the server, e.g. `send { putString("action", "accept") }`. */
fun UiScope.send(builder: CompoundTag.() -> Unit) = send(CompoundTag().apply(builder))
