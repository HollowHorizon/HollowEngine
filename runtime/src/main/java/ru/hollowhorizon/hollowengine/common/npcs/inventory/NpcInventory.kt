package ru.hollowhorizon.hollowengine.common.npcs.inventory

import net.minecraft.core.HolderLookup
import net.minecraft.core.NonNullList
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.ContainerHelper
import net.minecraft.world.item.ItemStack
import ru.hollowhorizon.hollowengine.common.npcs.items.ItemFilter
import ru.hollowhorizon.hollowengine.common.npcs.items.ItemRequest
import ru.hollowhorizon.hollowengine.common.utils.areStacksEqual
import kotlin.math.min

class NpcInventory(size: Int = DEFAULT_SIZE) {
    private var items: NonNullList<ItemStack> = createItems(size)

    val size: Int
        get() = items.size

    fun contents(): List<ItemStack> = items.map(ItemStack::copy)

    fun get(slot: Int): ItemStack {
        requireSlot(slot)
        return items[slot].copy()
    }

    fun set(slot: Int, stack: ItemStack): ItemStack {
        requireSlot(slot)
        val previous = items[slot]
        items[slot] = stack.copy()
        return previous
    }

    fun resize(size: Int): List<ItemStack> {
        val resized = createItems(size)
        val retained = min(items.size, resized.size)
        repeat(retained) { slot -> resized[slot] = items[slot] }
        val overflow = items.drop(retained).filterNot(ItemStack::isEmpty).map(ItemStack::copy)
        items = resized
        return overflow
    }

    fun insert(stack: ItemStack): ItemStack {
        if (stack.isEmpty) return ItemStack.EMPTY
        val remainder = stack.copy()

        items.forEach { existing ->
            if (remainder.isEmpty || existing.isEmpty || !existing.areStacksEqual(remainder)) return@forEach
            val moved = min(remainder.count, existing.maxStackSize - existing.count)
            if (moved > 0) {
                existing.grow(moved)
                remainder.shrink(moved)
            }
        }

        for (slot in items.indices) {
            if (remainder.isEmpty) break
            if (!items[slot].isEmpty) continue

            val moved = min(remainder.count, remainder.maxStackSize)
            items[slot] = remainder.copy().apply { count = moved }
            remainder.shrink(moved)
        }

        return remainder.takeUnless(ItemStack::isEmpty) ?: ItemStack.EMPTY
    }

    fun insertAll(stacks: Iterable<ItemStack>): List<ItemStack> =
        stacks.map(::insert).filterNot(ItemStack::isEmpty)

    fun extract(filter: ItemFilter, count: Int): List<ItemStack> {
        require(count >= 0) { "Extracted item count cannot be negative" }
        if (count == 0) return emptyList()

        var remaining = count
        val extracted = mutableListOf<ItemStack>()
        for (slot in items.indices) {
            if (remaining == 0) break
            val stack = items[slot]
            if (stack.isEmpty || !filter.matches(stack)) continue

            val taken = stack.split(min(stack.count, remaining))
            if (stack.isEmpty) items[slot] = ItemStack.EMPTY
            extracted += taken
            remaining -= taken.count
        }
        return extracted
    }

    fun count(filter: ItemFilter): Int = items.sumOf { stack ->
        stack.count.takeIf { !stack.isEmpty && filter.matches(stack) } ?: 0
    }

    fun contains(request: ItemRequest): Boolean = count(request.filter) >= request.count

    fun containsAll(requests: Iterable<ItemRequest>): Boolean {
        val available = items.map { it.count }.toMutableList()
        requests.forEach { request ->
            var needed = request.count
            for (slot in items.indices) {
                if (needed == 0) break
                val stack = items[slot]
                if (available[slot] == 0 || stack.isEmpty || !request.filter.matches(stack)) continue
                val used = min(needed, available[slot])
                available[slot] -= used
                needed -= used
            }
            if (needed > 0) return false
        }
        return true
    }

    fun clear(): List<ItemStack> {
        val removed = contents().filterNot(ItemStack::isEmpty)
        items.fill(ItemStack.EMPTY)
        return removed
    }

    fun save(tag: CompoundTag, registries: HolderLookup.Provider) {
        tag.putInt(SIZE_KEY, size)
        ContainerHelper.saveAllItems(tag, items, registries)
    }

    fun load(tag: CompoundTag, registries: HolderLookup.Provider) {
        val savedSize = tag.getInt(SIZE_KEY).takeIf { it > 0 } ?: DEFAULT_SIZE
        items = createItems(savedSize)
        ContainerHelper.loadAllItems(tag, items, registries)
    }

    private fun requireSlot(slot: Int) {
        require(slot in items.indices) { "Inventory slot $slot is outside 0 until ${items.size}" }
    }

    companion object {
        const val DEFAULT_SIZE = 36
        private const val MAX_SIZE = 256
        private const val SIZE_KEY = "Size"

        private fun createItems(size: Int): NonNullList<ItemStack> {
            require(size in 1..MAX_SIZE) { "NPC inventory size must be between 1 and $MAX_SIZE" }
            return NonNullList.withSize(size, ItemStack.EMPTY)
        }
    }
}
