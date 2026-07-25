package ru.hollowhorizon.hollowengine.common.slots

import net.minecraft.core.HolderLookup
import net.minecraft.core.NonNullList
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.ContainerHelper
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemStack
import kotlin.math.min

/**
 * Read/write access to a fixed run of item slots, the single abstraction the slot system binds to.
 *
 * Implementations adapt whatever actually stores the items (a vanilla `Container`, an NPC inventory,
 * a living entity's equipment), so zones, quick-move and click handling never learn about backends.
 * The interface stays deliberately smaller than `Container`: no display names, no ticking, no
 * ownership checks. Those belong to whoever opened the UI.
 *
 * [get] returns a copy and [set] stores one, so a caller may keep and mutate what it read without
 * writing through to the backing storage by accident.
 */
interface SlotSource {
    val size: Int

    operator fun get(slot: Int): ItemStack

    operator fun set(slot: Int, stack: ItemStack)

    /**
     * The storage's own cap on [slot], independent of any item. Per slot rather than per source so a
     * source can mix capacities, which is what lets equipment expose one-item armor slots next to hands
     * that hold a full stack.
     *
     * A zone publishes these to the client, so both sides compute the same room in a slot.
     */
    fun stackLimit(slot: Int): Int = DEFAULT_STACK_LIMIT

    /**
     * What [slot] accepts, as a description the client can evaluate too.
     *
     * This is the hook that keeps a restricted slot from flickering: state the rule here and the client
     * predicts the same refusal the server will make, instead of showing an item land and then snap back.
     * Only reach for [canPlace] when a rule genuinely cannot be described.
     */
    fun slotFilter(slot: Int): SlotFilter = SlotFilter.Any

    /** How many of [stack] fit in [slot]; the storage cap and the item's own limit, whichever is smaller. */
    fun slotLimit(slot: Int, stack: ItemStack): Int = min(stackLimit(slot), stack.maxStackSize)

    /**
     * Whether the storage accepts [stack] in [slot]. Derived from [slotFilter] by default; override only
     * for a check that cannot travel, and expect the client to occasionally mispredict it.
     */
    fun canPlace(slot: Int, stack: ItemStack): Boolean = slotFilter(slot).matches(stack)

    /** Called once after a batch of writes, for backings that must persist or broadcast the change. */
    fun setChanged() {}

    /**
     * Identity of the physical slot [slot] stands for.
     *
     * Two zones bound to the same underlying slot would each hold their own snapshot of it, and two clicks
     * arriving before the next tick could take the same stack twice. Declaration compares these keys and
     * refuses the overlap, so a source has to say when it is a view of storage something else also sees:
     * a range over another source, or one of an entity's equipment slots.
     */
    fun storageSlotKey(slot: Int): Any = SlotStorageKey(this, slot)

    /**
     * Whether [player] may still reach this storage.
     *
     * Checked every tick and again before every click, and a false answer closes the UI. This is the
     * engine's own guard, not a policy a script opts into: without it a client that simply never sends its
     * close packet could keep operating a chest from another dimension.
     */
    fun isAccessibleTo(player: ServerPlayer): Boolean = true

    val indices: IntRange get() = 0 until size
}

/** Default [SlotSource.storageSlotKey]: the source instance paired with the index it was asked about. */
data class SlotStorageKey(val storage: Any, val slot: Any)

/** Reach vanilla allows for a container, and the default for anything bound to a world object. */
const val DEFAULT_SLOT_REACH = 8.0

/**
 * Ties [source]'s reachability to [owner], for storage that has no world identity of its own.
 *
 * An NPC's inventory is the case in point: the items live in a plain list, so nothing about them says the
 * player has to be standing next to the NPC.
 */
fun SlotSource.reachableFrom(owner: Entity, maxDistance: Double = DEFAULT_SLOT_REACH): SlotSource =
    ReachableSlotSource(this, owner, maxDistance)

private class ReachableSlotSource(
    private val delegate: SlotSource,
    private val owner: Entity,
    private val maxDistance: Double,
) : SlotSource by delegate {
    override fun isAccessibleTo(player: ServerPlayer): Boolean =
        delegate.isAccessibleTo(player) && owner.canBeReachedBy(player, maxDistance)
}

/** Alive, in the same world, and close enough. Trivially true when the player is the owner. */
internal fun Entity.canBeReachedBy(player: ServerPlayer, maxDistance: Double = DEFAULT_SLOT_REACH): Boolean {
    if (this === player) return !player.isRemoved
    if (isRemoved) return false
    if (this is LivingEntity && !isAlive) return false
    if (level() !== player.level()) return false
    return distanceToSqr(player) <= maxDistance * maxDistance
}

/**
 * A plain in-memory [SlotSource] over a fixed-size list. It owns its items, so it also serializes
 * them: NPC inventories are built on this rather than reimplementing storage.
 */
class SimpleSlotSource(size: Int) : SlotSource {
    private var items: NonNullList<ItemStack> = NonNullList.withSize(size, ItemStack.EMPTY)

    override val size: Int
        get() = items.size

    override fun get(slot: Int): ItemStack {
        requireSlot(slot)
        return items[slot].copy()
    }

    override fun set(slot: Int, stack: ItemStack) {
        requireSlot(slot)
        items[slot] = stack.copy()
    }

    fun contents(): List<ItemStack> = items.map(ItemStack::copy)

    /** Resizes in place, returning the non-empty stacks that no longer fit. */
    fun resize(size: Int): List<ItemStack> {
        val resized = NonNullList.withSize(size, ItemStack.EMPTY)
        val retained = min(items.size, resized.size)
        repeat(retained) { slot -> resized[slot] = items[slot] }
        val overflow = items.drop(retained).filterNot(ItemStack::isEmpty).map(ItemStack::copy)
        items = resized
        return overflow
    }

    fun fillEmpty() = items.fill(ItemStack.EMPTY)

    fun saveItems(tag: CompoundTag, registries: HolderLookup.Provider) {
        ContainerHelper.saveAllItems(tag, items, registries)
    }

    fun loadItems(tag: CompoundTag, registries: HolderLookup.Provider, size: Int) {
        items = NonNullList.withSize(size, ItemStack.EMPTY)
        ContainerHelper.loadAllItems(tag, items, registries)
    }

    private fun requireSlot(slot: Int) {
        require(slot in items.indices) { "Slot $slot is outside 0 until ${items.size}" }
    }
}
