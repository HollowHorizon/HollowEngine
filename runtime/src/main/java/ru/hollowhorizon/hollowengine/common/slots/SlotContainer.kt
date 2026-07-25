package ru.hollowhorizon.hollowengine.common.slots

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import ru.hollowhorizon.hollowengine.common.slots.net.SlotChange
import ru.hollowhorizon.hollowengine.common.slots.net.SlotSyncPacket
import ru.hollowhorizon.hollowengine.common.ui.net.UiSession
import ru.hollowhorizon.hollowengine.common.ui.net.UiSessionManager

/**
 * The server's authoritative view of one open slot UI.
 *
 * It owns the state the bound [SlotSource]s add up to, decides what a client's intent really did, and
 * ships the difference. Two things it deliberately does not do: interpret gestures (that is
 * [applyClick], which both sides share) and know how the UI looks.
 *
 * A click is all-or-nothing. The intent is applied to a scratch copy, every slot it touched is offered
 * to the storage and to the zone's handlers, and only an unopposed result is committed. A rejected
 * click is answered with a full snapshot, because the client has already shown its own prediction.
 */
class SlotContainer internal constructor(
    val session: UiSession,
    val layout: SlotLayout,
    private val bindings: Map<String, SlotZoneBinding>,
    private val validity: (() -> Boolean)?,
) {
    private val sources: Map<String, SlotSource> = bindings.mapValues { it.value.source }

    val state = SlotState(layout.totalSize)

    /** What the client was last told, so a patch carries only what actually moved. */
    private var sent = SlotState(layout.totalSize)

    var revision: Int = 0
        private set

    val player: ServerPlayer get() = session.player

    init {
        state.readFrom(layout, sources)
        sent = state.copy()
    }

    /**
     * Whether the UI may stay open.
     *
     * The player has to still be here and every bound storage has to still be reachable. Those are the
     * engine's own conditions and a script cannot drop them: a client that never sends its close packet
     * would otherwise go on operating a chest from another dimension. [validity] only narrows this further.
     */
    fun isValid(): Boolean {
        if (player.hasDisconnected() || player.isRemoved) return false
        if (sources.values.any { !it.isAccessibleTo(player) }) return false
        return validity?.invoke() ?: true
    }

    /**
     * Applies what the client asked for. [clientRevision] is the revision its prediction was based on:
     * anything else means it acted on stale contents, and the only safe answer is the truth.
     */
    internal fun handle(clientRevision: Int, intent: SlotIntent) {
        if (!session.isOpen) return
        if (!isValid()) return UiSessionManager.close(session)

        // Re-read before doing anything. The cached state can be behind a hopper, a script, or another
        // player's session on the same chest, and acting on a stale copy of a stack is how a click ends up
        // taking items that are no longer there.
        syncExternalChanges()
        if (clientRevision != revision) return sendFullSnapshot()

        val working = state.copy()
        val result = layout.applyClick(working, intent)
        // A gesture that did nothing still has to be answered. A client that cannot predict is waiting for
        // the reply before it sends anything else, and one that can predict has just disagreed with the
        // server about the outcome, so the truth is the only answer that fits both.
        if (!result.changed) return sendFullSnapshot()

        val touched = working.changedSlotsAgainst(state)
        if (!accepted(touched, working)) return sendFullSnapshot()

        // Deltas are computed against the pre-commit contents, so they have to be taken before committing
        // and replayed after: the after-hooks describe what the click did, not what the slot now holds.
        val deltas = touched.associateWith { slot -> SlotDelta.between(state[slot], working[slot]) }
        commit(touched, working)
        result.dropped.forEach { stack -> player.drop(stack, false) }
        notifyMoved(deltas)
        notifyChanged(touched)
        sendPatch(touched)
    }

    /**
     * Re-reads the storages and pushes anything that changed behind the UI's back: an NPC picking up loot,
     * a hopper filling a chest the player is looking at.
     *
     * An external change bumps the revision, which is what makes a client prediction built on the old
     * contents fail its check instead of being applied on top.
     */
    internal fun syncExternalChanges() {
        if (!session.isOpen) return
        val current = SlotState(layout.totalSize).also { it.readFrom(layout, sources) }
        val changed = current.changedSlotsAgainst(state)
        if (changed.isEmpty()) return
        changed.forEach { slot -> state[slot] = current[slot] }
        revision++
        sendPatch(changed)
    }

    /** Hands the cursor stack back so closing a UI can never destroy items. */
    internal fun releaseCarried() {
        val carried = state.carried
        if (carried.isEmpty) return
        state.carried = ItemStack.EMPTY
        val remainder = carried.copy()
        player.inventory.add(remainder)
        if (!remainder.isEmpty) player.drop(remainder, false)
    }

    /**
     * Whether the storages and the zone checks all allow the result in [working].
     *
     * Only pure tests run here. Nothing has been committed yet and a later slot may still refuse, so a hook
     * with an effect would leave that effect behind on a click that never happened; that is why effects live
     * in the after-hooks instead.
     */
    private fun accepted(touched: List<Int>, working: SlotState): Boolean = touched.all { slot ->
        val zone = layout.zoneOf(slot) ?: return@all false
        val binding = bindings[zone.name] ?: return@all false
        val local = slot - zone.offset
        val after = working[slot]

        if (!after.isEmpty) {
            if (!binding.source.canPlace(local, after)) return@all false
            // The layout's published limit should already agree, but the storage has the last word on how
            // much of an item its slot holds, and a client cannot be trusted to have respected it.
            if (after.count > binding.source.slotLimit(local, after)) return@all false
        }

        val delta = SlotDelta.between(state[slot], after)
        delta.inserted?.let { if (binding.allowInsert?.invoke(local, it) == false) return@all false }
        delta.extracted?.let { if (binding.allowExtract?.invoke(local, it) == false) return@all false }
        true
    }

    private fun commit(touched: List<Int>, working: SlotState) {
        val dirtySources = mutableSetOf<SlotSource>()
        touched.forEach { slot ->
            val zone = layout.zoneOf(slot) ?: return@forEach
            val source = sources[zone.name] ?: return@forEach
            source[slot - zone.offset] = working[slot]
            state[slot] = working[slot]
            dirtySources += source
        }
        state.carried = working.carried
        dirtySources.forEach(SlotSource::setChanged)
        revision++
    }

    /**
     * Tells the zones what moved, once it has actually moved.
     *
     * Runs after [commit], so an effect a script performs here cannot be stranded by a later refusal.
     */
    private fun notifyMoved(deltas: Map<Int, SlotDelta>) {
        deltas.forEach { (slot, delta) ->
            val zone = layout.zoneOf(slot) ?: return@forEach
            val binding = bindings[zone.name] ?: return@forEach
            val local = slot - zone.offset
            delta.inserted?.let { binding.afterInsert?.invoke(local, it) }
            delta.extracted?.let { binding.afterExtract?.invoke(local, it) }
        }
    }

    private fun notifyChanged(touched: List<Int>) {
        touched.forEach { slot ->
            val zone = layout.zoneOf(slot) ?: return@forEach
            // A copy: the state's stacks are the ones the next click reads, and a handler that keeps or
            // mutates what it was handed must not be able to reach into them.
            bindings[zone.name]?.onChange?.invoke(slot - zone.offset, state[slot].copy())
        }
    }

    private fun sendPatch(changed: List<Int>) {
        if (changed.isEmpty() && state.carried.sameAs(sent.carried)) return
        changed.forEach { slot -> sent[slot] = state[slot] }
        sent.carried = state.carried
        SlotSyncPacket(
            sessionId = session.id,
            revision = revision,
            changes = changed.map { SlotChange(it, state[it]) },
            carried = state.carried,
        ).send(player)
    }

    private fun sendFullSnapshot() {
        sent = state.copy()
        SlotSyncPacket(
            sessionId = session.id,
            revision = revision,
            changes = state.contents().mapIndexed { slot, stack -> SlotChange(slot, stack) },
            carried = state.carried,
            full = true,
        ).send(player)
    }
}
