package ru.hollowhorizon.hollowengine.common.slots

import kotlinx.serialization.Serializable
import net.minecraft.world.item.ItemStack
import ru.hollowhorizon.hollowengine.common.utils.areStacksEqual
import kotlin.math.max
import kotlin.math.min

enum class SlotIntentKind { CLICK, QUICK_MOVE, DROP, DROP_CARRIED, DISTRIBUTE }

enum class SlotButton { LEFT, RIGHT }

/** How a drag across several slots divides the carried stack. */
enum class SlotDistributeMode {
    /** Left-drag: an equal share each, the remainder staying on the cursor. */
    EVEN,

    /** Right-drag: exactly one item per slot. */
    SINGLE,
}

/**
 * Something the player did, in terms the slot logic understands.
 *
 * One flat type rather than a sealed hierarchy: an intent travels as a packet field, and the project's
 * NBT format has no polymorphism to lean on. Build them through the factories.
 */
@Serializable
class SlotIntent internal constructor(
    val kind: SlotIntentKind,
    val slot: Int = -1,
    val button: SlotButton = SlotButton.LEFT,
    val all: Boolean = false,
    val slots: List<Int> = emptyList(),
    val distribute: SlotDistributeMode = SlotDistributeMode.EVEN,
) {
    companion object {
        fun click(slot: Int, button: SlotButton) = SlotIntent(SlotIntentKind.CLICK, slot = slot, button = button)

        fun quickMove(slot: Int) = SlotIntent(SlotIntentKind.QUICK_MOVE, slot = slot)

        fun drop(slot: Int, all: Boolean) = SlotIntent(SlotIntentKind.DROP, slot = slot, all = all)

        fun dropCarried(all: Boolean) = SlotIntent(SlotIntentKind.DROP_CARRIED, all = all)

        fun distribute(slots: List<Int>, mode: SlotDistributeMode) =
            SlotIntent(SlotIntentKind.DISTRIBUTE, slots = slots, distribute = mode)
    }
}

/**
 * What applying an intent did. [dropped] is what has to become an item entity in the world, which only
 * the server acts on. The client discards it and lets the authoritative state come back.
 */
class SlotClickResult(val changed: Boolean, val dropped: List<ItemStack> = emptyList())

/**
 * Applies [intent] to [state] under this layout's rules.
 *
 * This is the whole behavior of a slot click, and it is deliberately pure: no world, no player, no
 * networking. The server runs it to decide what really happened; the client runs the identical call to
 * show the result immediately. Anything that cannot be expressed here (a custom filter, a handler that may
 * cancel) makes the layout unpredictable rather than getting a second, divergent implementation.
 */
fun SlotLayout.applyClick(state: SlotState, intent: SlotIntent): SlotClickResult = when (intent.kind) {
    SlotIntentKind.CLICK -> when (intent.button) {
        SlotButton.LEFT -> SlotClickResult(leftClick(state, intent.slot))
        SlotButton.RIGHT -> SlotClickResult(rightClick(state, intent.slot))
    }

    SlotIntentKind.QUICK_MOVE -> SlotClickResult(quickMove(state, intent.slot))
    SlotIntentKind.DROP -> dropFromSlot(state, intent.slot, intent.all)
    SlotIntentKind.DROP_CARRIED -> dropCarried(state, intent.all)
    SlotIntentKind.DISTRIBUTE -> SlotClickResult(distribute(state, intent.slots, intent.distribute))
}

private fun SlotLayout.copyClick(state: SlotState, slot: Int, count: Int): Boolean {
    val rules = rulesAt(slot)
    val carried = state.carried
    val current = state[slot]

    if (carried.isEmpty) {
        if (current.isEmpty) return false
        state[slot] = ItemStack.EMPTY
        return true
    }

    if (!rules.canInsert.matches(carried)) return false
    val placed = min(count, effectiveLimit(rules, carried))
    if (placed <= 0) return false
    val next = carried.copyWithCount(placed)
    if (current.areStacksEqual(next) && current.count == next.count) return false
    state[slot] = next
    return true
}

private fun SlotLayout.leftClick(state: SlotState, slot: Int): Boolean {
    if (slot !in 0 until totalSize) return false
    if (copiesAt(slot)) return copyClick(state, slot, state.carried.count)
    val rules = rulesAt(slot)
    val current = state[slot]
    val carried = state.carried

    if (carried.isEmpty) {
        if (current.isEmpty || !rules.canExtract.matches(current)) return false
        state.carried = current
        state[slot] = ItemStack.EMPTY
        return true
    }

    if (current.isEmpty) {
        if (!rules.canInsert.matches(carried)) return false
        val moved = min(carried.count, effectiveLimit(rules, carried))
        if (moved <= 0) return false
        state[slot] = carried.copyWithCount(moved)
        state.carried = carried.without(moved)
        return true
    }

    if (current.areStacksEqual(carried)) {
        val moved = min(carried.count, effectiveLimit(rules, current) - current.count)
        if (moved <= 0) return false
        state[slot] = current.copyWithCount(current.count + moved)
        state.carried = carried.without(moved)
        return true
    }

    // Different items: swap, but only when the whole carried stack fits. A partial swap would leave items
    // in two places at once, which no vanilla gesture does either.
    if (!rules.canInsert.matches(carried) || !rules.canExtract.matches(current)) return false
    if (carried.count > effectiveLimit(rules, carried)) return false
    state[slot] = carried
    state.carried = current
    return true
}

private fun SlotLayout.rightClick(state: SlotState, slot: Int): Boolean {
    if (slot !in 0 until totalSize) return false
    if (copiesAt(slot)) return copyClick(state, slot, count = 1)
    val rules = rulesAt(slot)
    val current = state[slot]
    val carried = state.carried

    if (carried.isEmpty) {
        if (current.isEmpty || !rules.canExtract.matches(current)) return false
        val taken = (current.count + 1) / 2
        state.carried = current.copyWithCount(taken)
        state[slot] = current.without(taken)
        return true
    }

    if (current.isEmpty) {
        if (!rules.canInsert.matches(carried) || effectiveLimit(rules, carried) < 1) return false
        state[slot] = carried.copyWithCount(1)
        state.carried = carried.without(1)
        return true
    }

    if (!current.areStacksEqual(carried)) return false
    if (current.count >= effectiveLimit(rules, current)) return false
    state[slot] = current.copyWithCount(current.count + 1)
    state.carried = carried.without(1)
    return true
}

/**
 * Sends a slot's contents around the ring, trying each following zone in turn and stopping at the first
 * that took anything. Zones that accept nothing are skipped rather than swallowing the gesture, so a
 * filtered zone in the middle of the ring never makes shift-click look broken.
 */
private fun SlotLayout.quickMove(state: SlotState, slot: Int): Boolean {
    if (slot !in 0 until totalSize) return false
    val zone = zoneOf(slot) ?: return false
    if (!zone.role.canSend) return false
    val rules = rulesAt(slot)
    val stack = state[slot]
    if (stack.isEmpty || !rules.canExtract.matches(stack)) return false

    var remainder = stack.copy()
    for (target in quickMoveTargets(zone.name)) {
        remainder = SlotStateZoneView(state, target, this).insert(remainder)
        if (remainder.isEmpty) break
    }
    if (remainder.count == stack.count) return false
    state[slot] = remainder
    return true
}

private fun SlotLayout.dropFromSlot(state: SlotState, slot: Int, all: Boolean): SlotClickResult {
    if (slot !in 0 until totalSize) return SlotClickResult(false)
    if (copiesAt(slot)) {
        if (state[slot].isEmpty) return SlotClickResult(false)
        state[slot] = ItemStack.EMPTY
        return SlotClickResult(true)
    }
    val rules = rulesAt(slot)
    val current = state[slot]
    if (current.isEmpty || !rules.canExtract.matches(current)) return SlotClickResult(false)
    val taken = if (all) current.count else 1
    state[slot] = current.without(taken)
    return SlotClickResult(true, listOf(current.copyWithCount(taken)))
}

private fun dropCarried(state: SlotState, all: Boolean): SlotClickResult {
    val carried = state.carried
    if (carried.isEmpty) return SlotClickResult(false)
    val taken = if (all) carried.count else 1
    state.carried = carried.without(taken)
    return SlotClickResult(true, listOf(carried.copyWithCount(taken)))
}

/**
 * Spreads the carried stack over the slots a drag passed through. Mirrors vanilla: an even split hands
 * out `count / targets` each and whatever does not divide stays on the cursor.
 */
private fun SlotLayout.distribute(state: SlotState, slots: List<Int>, mode: SlotDistributeMode): Boolean {
    val carried = state.carried
    if (carried.isEmpty || slots.isEmpty()) return false

    val targets = slots.distinct().filter { slot ->
        slot in 0 until totalSize && !copiesAt(slot) && acceptsForDistribution(state, slot, carried)
    }
    if (targets.isEmpty()) return false

    val perSlot = when (mode) {
        SlotDistributeMode.SINGLE -> 1
        SlotDistributeMode.EVEN -> max(1, carried.count / targets.size)
    }

    var remaining = carried
    var changed = false
    for (slot in targets) {
        if (remaining.isEmpty) break
        val rules = rulesAt(slot)
        val current = state[slot]
        val room = effectiveLimit(rules, remaining) - current.count
        val moved = min(min(perSlot, remaining.count), room)
        if (moved <= 0) continue
        state[slot] = remaining.copyWithCount(current.count + moved)
        remaining = remaining.without(moved)
        changed = true
    }
    state.carried = remaining
    return changed
}

private fun SlotLayout.acceptsForDistribution(state: SlotState, slot: Int, carried: ItemStack): Boolean {
    val rules = rulesAt(slot)
    if (!rules.canInsert.matches(carried)) return false
    val current = state[slot]
    if (!current.isEmpty && !current.areStacksEqual(carried)) return false
    return current.count < effectiveLimit(rules, carried)
}

private fun effectiveLimit(rules: SlotRules, stack: ItemStack): Int =
    min(rules.stackLimit, stack.maxStackSize)

/**
 * One zone of a [SlotState] seen as a [SlotSource], so quick-move reuses the engine's single stacking
 * implementation instead of restating "top up matching stacks, then fill empty ones".
 */
private class SlotStateZoneView(
    private val state: SlotState,
    private val zone: SlotZoneLayout,
    private val layout: SlotLayout,
) : SlotSource {
    override val size: Int get() = zone.size

    override fun get(slot: Int): ItemStack = state[zone.offset + slot].copy()

    override fun set(slot: Int, stack: ItemStack) {
        state[zone.offset + slot] = stack
    }

    override fun slotLimit(slot: Int, stack: ItemStack): Int =
        effectiveLimit(layout.rulesAt(zone.offset + slot), stack)

    override fun canPlace(slot: Int, stack: ItemStack): Boolean =
        layout.rulesAt(zone.offset + slot).canInsert.matches(stack)
}
