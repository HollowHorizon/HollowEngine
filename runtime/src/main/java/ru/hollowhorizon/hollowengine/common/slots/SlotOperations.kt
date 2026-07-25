package ru.hollowhorizon.hollowengine.common.slots

import net.minecraft.world.item.ItemStack
import ru.hollowhorizon.hollowengine.common.npcs.items.ItemFilter
import ru.hollowhorizon.hollowengine.common.npcs.items.ItemRequest
import ru.hollowhorizon.hollowengine.common.utils.areStacksEqual
import kotlin.math.min

/**
 * The engine's stacking rules, written once against [SlotSource].
 *
 * Every path that moves items through storage goes through here: a player's shift-click, an NPC picking up
 * loot, a script calling `npc.giveItem`. The answer to "does this fit, and where" cannot drift between the
 * UI and the rest of the engine.
 */

/** Inserts what fits, topping up matching stacks before opening empty slots. Returns the remainder. */
fun SlotSource.insert(stack: ItemStack): ItemStack {
    if (stack.isEmpty) return ItemStack.EMPTY
    val remainder = stack.copy()
    var changed = false

    // Partially filled stacks first: filling an empty slot while a half-full one exists would
    // fragment storage and make a second insert of the same item fail for no visible reason.
    for (slot in indices) {
        if (remainder.isEmpty) break
        val existing = this[slot]
        if (existing.isEmpty || !existing.areStacksEqual(remainder)) continue
        val moved = min(remainder.count, slotLimit(slot, existing) - existing.count)
        if (moved <= 0) continue
        existing.grow(moved)
        this[slot] = existing
        remainder.shrink(moved)
        changed = true
    }

    for (slot in indices) {
        if (remainder.isEmpty) break
        if (!this[slot].isEmpty || !canPlace(slot, remainder)) continue
        val moved = min(remainder.count, slotLimit(slot, remainder))
        if (moved <= 0) continue
        this[slot] = remainder.copyWithCount(moved)
        remainder.shrink(moved)
        changed = true
    }

    if (changed) setChanged()
    return remainder.takeUnless(ItemStack::isEmpty) ?: ItemStack.EMPTY
}

/** Inserts each stack, returning only the leftovers that did not fit. */
fun SlotSource.insertAll(stacks: Iterable<ItemStack>): List<ItemStack> =
    stacks.map(::insert).filterNot(ItemStack::isEmpty)

/** Takes up to [count] items matching [filter], as the stacks they were stored in. */
fun SlotSource.extract(filter: ItemFilter, count: Int): List<ItemStack> {
    require(count >= 0) { "Extracted item count cannot be negative" }
    if (count == 0) return emptyList()

    var remaining = count
    val extracted = mutableListOf<ItemStack>()
    for (slot in indices) {
        if (remaining == 0) break
        val stack = this[slot]
        if (stack.isEmpty || !filter.matches(stack)) continue

        val taken = stack.split(min(stack.count, remaining))
        this[slot] = stack
        extracted += taken
        remaining -= taken.count
    }
    if (extracted.isNotEmpty()) setChanged()
    return extracted
}

fun SlotSource.count(filter: ItemFilter): Int = indices.sumOf { slot ->
    val stack = this[slot]
    stack.count.takeIf { !stack.isEmpty && filter.matches(stack) } ?: 0
}

fun SlotSource.contains(request: ItemRequest): Boolean = count(request.filter) >= request.count

/**
 * Whether all [requests] can be satisfied at once. Counted against a shared budget, so two requests
 * matching the same stack cannot both claim it.
 */
fun SlotSource.containsAll(requests: Iterable<ItemRequest>): Boolean {
    val stacks = indices.map { this[it] }
    val available = stacks.map { it.count }.toMutableList()
    requests.forEach { request ->
        var needed = request.count
        for (slot in stacks.indices) {
            if (needed == 0) break
            val stack = stacks[slot]
            if (available[slot] == 0 || stack.isEmpty || !request.filter.matches(stack)) continue
            val used = min(needed, available[slot])
            available[slot] -= used
            needed -= used
        }
        if (needed > 0) return false
    }
    return true
}

/** Empties every slot, returning what was removed. */
fun SlotSource.clearAll(): List<ItemStack> {
    val removed = indices.map { this[it] }.filterNot(ItemStack::isEmpty)
    if (removed.isEmpty()) return emptyList()
    indices.forEach { slot -> this[slot] = ItemStack.EMPTY }
    setChanged()
    return removed
}
