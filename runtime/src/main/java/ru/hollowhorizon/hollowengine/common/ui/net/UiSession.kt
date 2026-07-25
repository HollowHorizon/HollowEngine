package ru.hollowhorizon.hollowengine.common.ui.net

import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import ru.hollowhorizon.hollowengine.common.data.DataKey
import ru.hollowhorizon.hollowengine.common.data.encode
import ru.hollowhorizon.hollowengine.common.slots.SlotContainer

/** Whether a session drives a full screen or a HUD overlay. */
enum class UiSurfaceKind { SCREEN, OVERLAY }

/**
 * A server's handle on one open piece of UI. The server writes into the session's document and the
 * changes travel to the client as patches; the client's events come back through [onEvent].
 *
 * Values are addressed by [DataKey] - the same typed NBT key NPCs and story state use, so a change
 * ships exactly the key that changed, and a HUD bound to a single fast-moving value never resends
 * the rest of the document.
 *
 * The session is the unit of ownership: closing it closes the UI, and a disconnect drops it.
 */
class UiSession internal constructor(
    val id: Int,
    val player: ServerPlayer,
    val surface: ResourceLocation,
    val kind: UiSurfaceKind,
) {
    private val state = CompoundTag()
    private var eventHandler: ((CompoundTag) -> Unit)? = null
    private var closeHandler: (() -> Unit)? = null
    private val internalCloseHandlers = mutableListOf<() -> Unit>()

    var isOpen: Boolean = true
        internal set

    /**
     * The slots this UI declared, if any; see
     * [ru.hollowhorizon.hollowengine.common.slots.slots].
     */
    var slotContainer: SlotContainer? = null
        internal set

    /** The state as last sent; useful for reading back what the client currently shows. */
    fun state(): CompoundTag = state.copy()

    operator fun <T : Any> set(key: DataKey<T>, value: T) = put(key, value)

    /** Writes [value] at [key] and pushes just that key to the client. */
    fun <T : Any> put(key: DataKey<T>, value: T) {
        val tag = key.encode(value)
        state.put(key.name, tag)
        if (isOpen) PatchUiDataPacket(id, CompoundTag().apply { put(key.name, tag) }).send(player)
    }

    /**
     * Batches several writes into a single patch. Values are written inside the block with [put],
     * which here records into the batch instead of sending immediately:
     * `edit { put(Title, "x"); put(Progress, 3) }`.
     */
    fun edit(block: UiSessionBatch.() -> Unit) {
        val batch = UiSessionBatch()
        batch.block()
        if (batch.patch.isEmpty) return
        batch.patch.allKeys.forEach { key -> batch.patch.get(key)?.let { state.put(key, it) } }
        if (isOpen) PatchUiDataPacket(id, batch.patch).send(player)
    }

    /** Removes [key] from the document on both sides. */
    fun <T : Any> remove(key: DataKey<T>) {
        state.remove(key.name)
        if (isOpen) PatchUiDataPacket(id, CompoundTag(), listOf(key.name)).send(player)
    }

    /** Handles payloads the scripted UI sends with `send { }`. */
    fun onEvent(handler: (CompoundTag) -> Unit) {
        eventHandler = handler
    }

    /** Runs when the UI closes, whether the server or the player closed it. */
    fun onClose(handler: () -> Unit) {
        closeHandler = handler
    }

    /**
     * Registers engine-side cleanup for this session. Separate from [onClose] so a feature the session
     * carries, such as slots handing the cursor stack back, cannot be switched off by a script that
     * sets its own close handler.
     */
    internal fun onCloseInternal(handler: () -> Unit) {
        internalCloseHandlers += handler
    }

    fun close() = UiSessionManager.close(this)

    internal fun initialState(): CompoundTag = state.copy()

    internal fun seed(initial: CompoundTag) {
        initial.allKeys.forEach { key -> initial.get(key)?.let { state.put(key, it.copy()) } }
    }

    internal fun dispatchEvent(payload: CompoundTag) {
        eventHandler?.invoke(payload)
    }

    internal fun dispatchClose() {
        isOpen = false
        // Engine cleanup first: a script's close handler should see a settled world, with any items the
        // player was holding already back where they belong.
        internalCloseHandlers.forEach { it() }
        closeHandler?.invoke()
    }
}

/** Collects writes for [UiSession.edit] into one patch. */
class UiSessionBatch internal constructor() {
    internal val patch = CompoundTag()

    fun <T : Any> put(key: DataKey<T>, value: T) {
        patch.put(key.name, key.encode(value))
    }
}
