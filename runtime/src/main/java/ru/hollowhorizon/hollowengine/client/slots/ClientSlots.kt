package ru.hollowhorizon.hollowengine.client.slots

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import net.minecraft.world.item.ItemStack
import ru.hollowhorizon.hollowengine.common.slots.SlotIntent
import ru.hollowhorizon.hollowengine.common.slots.SlotLayout
import ru.hollowhorizon.hollowengine.common.slots.SlotState
import ru.hollowhorizon.hollowengine.common.slots.applyClick
import ru.hollowhorizon.hollowengine.common.slots.net.SlotChange
import ru.hollowhorizon.hollowengine.common.slots.net.SlotIntentPacket

/**
 * The client's mirror of one open slot UI.
 *
 * Contents live in Compose state keyed per slot, so a patch that moves one stack recomposes the slots
 * that show it and nothing else. The mirror is a prediction: a gesture is applied locally at once and
 * sent quoting the revision it assumed, and the server either confirms by advancing that revision or
 * refuses and replies with the truth. Without this the cursor would visibly lag the mouse by a round
 * trip on any real connection.
 *
 * When the client cannot fully evaluate a layout (a server-only filter, a handler that may cancel) it
 * predicts nothing, and keeps one gesture in flight at a time: a second would quote a revision the server
 * has already left behind.
 */
class ClientSlotSession internal constructor(
    val sessionId: Int,
    val layout: SlotLayout,
) {
    private val state = SlotState(layout.totalSize)
    private val stacks = mutableStateMapOf<Int, ItemStack>()
    private var carriedState = mutableStateOf(ItemStack.EMPTY)

    /** The revision the visible contents correspond to; a prediction advances it ahead of the server. */
    private var revision = 0

    /** True while an unpredicted gesture is awaiting its answer. */
    private var awaitingAck = false

    /** Contents as they were before a drag started, so each step of it recomputes from the same base. */
    private var previewBase: SlotState? = null

    val carried: ItemStack get() = carriedState.value

    operator fun get(slot: Int): ItemStack = stacks[slot] ?: ItemStack.EMPTY

    fun get(zone: String, local: Int): ItemStack {
        val flat = layout.flatIndex(zone, local)
        return if (flat < 0) ItemStack.EMPTY else this[flat]
    }

    /**
     * Remembers the current contents so a gesture in progress can be shown and reshown without sending
     * anything. A drag grows one slot at a time, and each step has to be recomputed from where the drag
     * started rather than stacked on the previous step's result.
     */
    fun beginPreview() {
        if (layout.isPredictable) previewBase = state.copy()
    }

    /** Shows what [intent] would do, from the state [beginPreview] captured. Sends nothing. */
    fun preview(intent: SlotIntent) {
        val base = previewBase ?: return
        state.restoreFrom(base)
        layout.applyClick(state, intent)
        publish()
    }

    /** Drops a preview and puts the visible contents back. */
    fun cancelPreview() {
        val base = previewBase ?: return
        previewBase = null
        state.restoreFrom(base)
        publish()
    }

    /** Applies [intent] locally when that is safe, and sends it either way. */
    fun submit(intent: SlotIntent) {
        // A preview is only ever a picture of the gesture now being committed; the real apply starts from
        // the state before it so the two cannot compound.
        previewBase?.let { base ->
            previewBase = null
            state.restoreFrom(base)
        }

        if (layout.isPredictable) {
            val result = layout.applyClick(state, intent)
            // A gesture that changes nothing locally would change nothing on the server either, so it is
            // not worth a packet, and sending it would desynchronize the revision.
            if (!result.changed) return
            revision++
            publish()
            SlotIntentPacket(sessionId, revision - 1, intent).send()
            return
        }

        if (awaitingAck) return
        awaitingAck = true
        SlotIntentPacket(sessionId, revision, intent).send()
    }

    internal fun reset(contents: List<ItemStack>, carried: ItemStack, revision: Int) {
        previewBase = null
        state.replaceAll(contents)
        state.carried = carried
        this.revision = revision
        awaitingAck = false
        publish()
    }

    internal fun apply(revision: Int, changes: List<SlotChange>, carried: ItemStack, full: Boolean) {
        if (full) {
            val contents = MutableList<ItemStack>(layout.totalSize) { ItemStack.EMPTY }
            changes.forEach { change ->
                if (change.slot in contents.indices) contents[change.slot] = change.stack
            }
            reset(contents, carried, revision)
            return
        }
        changes.forEach { change ->
            if (change.slot in 0 until layout.totalSize) state[change.slot] = change.stack
        }
        state.carried = carried
        this.revision = revision
        awaitingAck = false
        publish()
    }

    private fun publish() {
        for (slot in 0 until layout.totalSize) {
            val stack = state[slot]
            val shown = stacks[slot]
            if (shown == null || !sameContents(shown, stack)) stacks[slot] = stack.copy()
        }
        if (!sameContents(carriedState.value, state.carried)) carriedState.value = state.carried.copy()
    }

    private fun sameContents(a: ItemStack, b: ItemStack): Boolean {
        if (a.isEmpty && b.isEmpty) return true
        return a.count == b.count && ItemStack.isSameItemSameComponents(a, b)
    }
}

/**
 * The slot UIs this client has open, keyed by UI session.
 *
 * Separate from the session's [ru.hollowhorizon.hollowengine.common.ui.UiData] document: slot contents
 * change on every click, carry item components, and need a revision to reconcile against. The generic
 * NBT patch channel is built for none of that.
 */
object ClientSlots {
    private val sessions = mutableStateMapOf<Int, ClientSlotSession>()

    operator fun get(sessionId: Int): ClientSlotSession? = sessions[sessionId]

    internal fun open(
        sessionId: Int,
        layout: SlotLayout,
        contents: List<ItemStack>,
        carried: ItemStack,
        revision: Int,
    ) {
        sessions[sessionId] = ClientSlotSession(sessionId, layout).apply { reset(contents, carried, revision) }
    }

    internal fun sync(
        sessionId: Int,
        revision: Int,
        changes: List<SlotChange>,
        carried: ItemStack,
        full: Boolean,
    ) {
        sessions[sessionId]?.apply(revision, changes, carried, full)
    }

    /** Drops a session's mirror; the server has already dealt with anything left on the cursor. */
    fun close(sessionId: Int) {
        sessions.remove(sessionId)
    }

    fun clear() = sessions.clear()
}
