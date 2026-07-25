package ru.hollowhorizon.hollowengine.common.slots

import net.minecraft.world.entity.player.Player

/** Zone names the `PlayerInventory()` widget looks for. Both sides agree on them through this file. */
object PlayerSlotZones {
    const val STORAGE = "player"
    const val ARMOR = "armor"
    const val OFFHAND = "offhand"
}

/**
 * Declares the player's own slots, the half of a container screen that is the same every time.
 *
 * Storage is one zone rather than "hotbar" plus "main", because that is what quick-move should treat as a
 * unit: shift-clicking out of the player's inventory belongs in the *other* container, never in their own
 * hotbar. The familiar split into three rows and a hotbar is a layout question, and the widget answers it
 * by drawing ranges of this single zone.
 *
 * Armor and the off-hand stay out of the ring by default. They hold one specific item each, and a stray
 * shift-click landing a helmet on the player's head instead of in the chest they opened is a surprise
 * nobody asked for; pass a role explicitly to opt into it.
 */
fun SlotZonesBuilder.playerZones(
    player: Player,
    storage: SlotZoneRole = SlotZoneRole.BOTH,
    armor: SlotZoneRole? = SlotZoneRole.NONE,
    offhand: SlotZoneRole? = SlotZoneRole.NONE,
) {
    val inventory = ContainerSource(player.inventory)
    zone(PlayerSlotZones.STORAGE, inventory.range(0, STORAGE_SIZE)) { role = storage }
    armor?.let { armorRole ->
        // Head first: the zone is drawn top to bottom, and Inventory's own armor order is the reverse.
        zone(PlayerSlotZones.ARMOR, EquipmentSource(player, EquipmentSource.Armor)) { role = armorRole }
    }
    offhand?.let { offhandRole ->
        zone(PlayerSlotZones.OFFHAND, EquipmentSource(player, EquipmentSource.Offhand)) { role = offhandRole }
    }
}

/** Hotbar plus the three rows above it: every slot a player can shift-click into. */
const val STORAGE_SIZE = 36

/** Slots the hotbar occupies at the start of the storage zone. */
const val HOTBAR_SIZE = 9
