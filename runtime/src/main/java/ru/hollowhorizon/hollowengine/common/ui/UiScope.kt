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

    /** True while a server session backs this UI; false for a purely client-side overlay. */
    val isServerBound: Boolean

    /** Sends a payload to the server session that opened this UI. No-op when not server-bound. */
    fun send(payload: CompoundTag)

    /** Closes this screen, or hides this overlay. */
    fun close()
}

/** Builds and sends a payload to the server, e.g. `send { putString("action", "accept") }`. */
fun UiScope.send(builder: CompoundTag.() -> Unit) = send(CompoundTag().apply(builder))
