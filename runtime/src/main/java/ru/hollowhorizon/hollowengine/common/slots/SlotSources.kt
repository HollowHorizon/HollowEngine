package ru.hollowhorizon.hollowengine.common.slots

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.Container
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemStack
import ru.hollowhorizon.hollowengine.common.entities.NpcEntity
import ru.hollowhorizon.hollowengine.common.npcs.inventory.NpcInventory

/**
 * The [SlotSource] adapters the engine ships with.
 *
 * These are plain constructors rather than a registry that guesses an adapter from an object's type:
 * a screen states which storage it means, and a new backend is a new class implementing [SlotSource]
 * including loader-specific ones, which is why nothing here mentions capabilities. NeoForge's
 * `IItemHandler` cannot be referenced from this module at all; an adapter for it belongs in the
 * NeoForge bootstrap.
 */

/** Any vanilla container: chests, hoppers, and a player's own [net.minecraft.world.entity.player.Inventory]. */
class ContainerSource(private val container: Container) : SlotSource {
    override val size: Int
        get() = container.containerSize

    override fun get(slot: Int): ItemStack = container.getItem(slot).copy()

    override fun set(slot: Int, stack: ItemStack) = container.setItem(slot, stack.copy())

    override fun stackLimit(slot: Int): Int = container.maxStackSize

    // Vanilla's check is arbitrary code, so it cannot be described to the client. Containers that restrict
    // their slots (a furnace's fuel slot, say) may therefore show a brief snap-back on a refused click.
    override fun canPlace(slot: Int, stack: ItemStack): Boolean = container.canPlaceItem(slot, stack)

    override fun setChanged() = container.setChanged()

    override fun storageSlotKey(slot: Int): Any = SlotStorageKey(container, slot)

    /** Vanilla's own reach rule: for a block container it checks the block is still there and near enough. */
    override fun isAccessibleTo(player: ServerPlayer): Boolean = container.stillValid(player)
}

/**
 * A living entity's equipment, exposed as slots in the order given.
 *
 * Writes go through `setItemSlot`, so vanilla replication carries the change to every client that can
 * see the entity: an NPC re-armed through a slot UI updates on its model without the slot system
 * syncing anything itself.
 */
class EquipmentSource(
    private val entity: LivingEntity,
    private val slots: List<EquipmentSlot>,
) : SlotSource {
    override val size: Int
        get() = slots.size

    override fun get(slot: Int): ItemStack = entity.getItemBySlot(slots[slot]).copy()

    override fun set(slot: Int, stack: ItemStack) = entity.setItemSlot(slots[slot], stack.copy())

    /** Hands hold a full stack; a worn slot holds one thing however high the item itself stacks. */
    override fun stackLimit(slot: Int): Int =
        if (slots[slot].type == EquipmentSlot.Type.HAND) DEFAULT_STACK_LIMIT else 1

    /** Hands take anything; a worn slot takes what can be worn there. */
    override fun slotFilter(slot: Int): SlotFilter {
        val target = slots[slot]
        return if (target.type == EquipmentSlot.Type.HAND) SlotFilter.Any else SlotFilter.equipment(target)
    }

    // Keyed by the equipment slot rather than the index, so binding armor and hands as two zones over the
    // same entity is fine while binding the same slot twice is caught.
    override fun storageSlotKey(slot: Int): Any = SlotStorageKey(entity, slots[slot])

    override fun isAccessibleTo(player: ServerPlayer): Boolean = entity.canBeReachedBy(player)

    companion object {
        val Armor = listOf(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)
        val Hands = listOf(EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND)
        val Offhand = listOf(EquipmentSlot.OFFHAND)
        val All = Armor + Hands
    }
}

/**
 * An NPC's own inventory, reachable only while the player is next to [npc].
 *
 * The items live in a plain list with no world identity, so the reach rule has to be attached explicitly;
 * take the overload without an NPC only for storage that really should be reachable from anywhere.
 */
fun NpcInventorySource(npc: NpcEntity, maxDistance: Double = DEFAULT_SLOT_REACH): SlotSource =
    npc.inventory.slots.reachableFrom(npc, maxDistance)

/** An NPC inventory with no reach rule of its own; see the [NpcEntity] overload. */
fun NpcInventorySource(inventory: NpcInventory): SlotSource = inventory.slots

/**
 * A contiguous run of another source.
 *
 * Useful when a zone genuinely covers only part of a storage. To split one zone visually, prefer a range on
 * the widget instead (see `SlotGrid`'s `range`).
 */
class SlotRangeSource(
    private val delegate: SlotSource,
    private val from: Int,
    override val size: Int,
) : SlotSource {
    init {
        require(from >= 0 && size > 0) { "Range must start at or after 0 and cover at least one slot" }
        require(from + size <= delegate.size) { "Range $from until ${from + size} exceeds the source's ${delegate.size} slots" }
    }

    override fun stackLimit(slot: Int): Int = delegate.stackLimit(from + slot)

    override fun slotFilter(slot: Int): SlotFilter = delegate.slotFilter(from + slot)

    override fun get(slot: Int): ItemStack = delegate[from + slot]

    override fun set(slot: Int, stack: ItemStack) {
        delegate[from + slot] = stack
    }

    override fun slotLimit(slot: Int, stack: ItemStack): Int = delegate.slotLimit(from + slot, stack)

    override fun canPlace(slot: Int, stack: ItemStack): Boolean = delegate.canPlace(from + slot, stack)

    override fun setChanged() = delegate.setChanged()

    // Delegated, so two ranges over the same storage are caught as the overlap they are.
    override fun storageSlotKey(slot: Int): Any = delegate.storageSlotKey(from + slot)

    override fun isAccessibleTo(player: ServerPlayer): Boolean = delegate.isAccessibleTo(player)
}

fun SlotSource.range(from: Int, size: Int): SlotSource = SlotRangeSource(this, from, size)
