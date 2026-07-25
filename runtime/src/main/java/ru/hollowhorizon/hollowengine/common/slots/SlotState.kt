package ru.hollowhorizon.hollowengine.common.slots

import net.minecraft.world.item.ItemStack

/**
 * The contents of every slot in one open UI, plus what the player is holding on the cursor.
 *
 * Both sides keep one of these: the server's is authoritative and mirrors the bound
 * [SlotSource]s, the client's is a prediction that a patch or a full snapshot corrects. Because it is
 * a plain value with no world behind it, the click logic that transforms it can be a pure function and
 * tested as one.
 */
class SlotState(val size: Int) {
    private val stacks = Array(size) { ItemStack.EMPTY }

    /** The stack on the cursor. Held per open UI, not per player, so closing one cannot leak into another. */
    var carried: ItemStack = ItemStack.EMPTY
        set(value) {
            field = if (value.isEmpty) ItemStack.EMPTY else value
        }

    operator fun get(slot: Int): ItemStack = stacks[slot]

    operator fun set(slot: Int, stack: ItemStack) {
        stacks[slot] = if (stack.isEmpty) ItemStack.EMPTY else stack
    }

    fun contents(): List<ItemStack> = stacks.map { it.copy() }

    fun replaceAll(contents: List<ItemStack>) {
        for (slot in 0 until size) {
            stacks[slot] = contents.getOrNull(slot)?.takeUnless(ItemStack::isEmpty)?.copy() ?: ItemStack.EMPTY
        }
    }

    /** Overwrites this state with [other]'s contents; used to rewind a preview. */
    fun restoreFrom(other: SlotState) {
        for (slot in 0 until size) stacks[slot] = other.stacks[slot].copy()
        carried = other.carried.copy()
    }

    fun copy(): SlotState = SlotState(size).also { copy ->
        for (slot in 0 until size) copy.stacks[slot] = stacks[slot].copy()
        copy.carried = carried.copy()
    }

    /** Pulls the current contents of [sources] into this state, in flat index order. */
    fun readFrom(layout: SlotLayout, sources: Map<String, SlotSource>) {
        layout.zones.forEach { zone ->
            val source = sources[zone.name] ?: return@forEach
            for (local in 0 until zone.size) {
                stacks[zone.offset + local] = if (local < source.size) source[local] else ItemStack.EMPTY
            }
        }
    }

    /** Flat indices whose contents differ from [other]; the unit a sync patch travels in. */
    fun changedSlotsAgainst(other: SlotState): List<Int> =
        (0 until size).filter { slot -> !stacks[slot].sameAs(other.stacks[slot]) }
}

/**
 * What actually entered and left a slot.
 *
 * A slot's before/after pair alone does not say which happened: 5 apples becoming 6 is an insert of one, not
 * an insert of six and an extract of five. Handlers that grant rewards or consume ingredients act on these,
 * so conflating the two directions would fire both on every ordinary click. Only a swap is genuinely both.
 *
 * Both stacks are copies, and both carry the amount that moved rather than the slot's total.
 */
class SlotDelta private constructor(val inserted: ItemStack?, val extracted: ItemStack?) {
    companion object {
        private val Nothing = SlotDelta(null, null)

        fun between(before: ItemStack, after: ItemStack): SlotDelta {
            if (before.isEmpty && after.isEmpty) return Nothing
            if (before.isEmpty) return SlotDelta(inserted = after.copy(), extracted = null)
            if (after.isEmpty) return SlotDelta(inserted = null, extracted = before.copy())

            // Different items in and out: the click swapped them, which is the one case that is both.
            if (!ItemStack.isSameItemSameComponents(before, after)) {
                return SlotDelta(inserted = after.copy(), extracted = before.copy())
            }

            val delta = after.count - before.count
            return when {
                delta > 0 -> SlotDelta(inserted = after.copyWithCount(delta), extracted = null)
                delta < 0 -> SlotDelta(inserted = null, extracted = before.copyWithCount(-delta))
                else -> Nothing
            }
        }
    }
}

/** Value comparison including count: what "this slot still shows the same thing" means. */
internal fun ItemStack.sameAs(other: ItemStack): Boolean {
    if (isEmpty && other.isEmpty) return true
    return count == other.count && ItemStack.isSameItemSameComponents(this, other)
}

/** A copy with [amount] fewer items, or empty when nothing is left. */
internal fun ItemStack.without(amount: Int): ItemStack {
    val remaining = count - amount
    return if (remaining <= 0) ItemStack.EMPTY else copyWithCount(remaining)
}
