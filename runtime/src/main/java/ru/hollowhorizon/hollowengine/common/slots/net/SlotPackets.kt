@file:UseSerializers(ForItemStack::class)

package ru.hollowhorizon.hollowengine.common.slots.net

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import ru.hollowhorizon.hollowengine.client.slots.ClientSlots
import ru.hollowhorizon.hollowengine.common.network.HollowPacket
import ru.hollowhorizon.hollowengine.common.network.HollowPacketHandler
import ru.hollowhorizon.hollowengine.common.slots.SlotContainers
import ru.hollowhorizon.hollowengine.common.slots.SlotIntent
import ru.hollowhorizon.hollowengine.common.slots.SlotLayout
import ru.hollowhorizon.hollowengine.common.utils.nbt.ForItemStack

/** One slot's new contents. Patches carry these rather than whole inventories. */
@Serializable
class SlotChange(val slot: Int, val stack: ItemStack)

/**
 * Opens the slot side of a UI session: the structure, and the contents to start from.
 *
 * Sent from inside the session body, so it arrives before the screen does and the first composition
 * already knows which zones exist.
 */
@HollowPacketHandler(HollowPacketHandler.Direction.TO_CLIENT)
@Serializable
class SlotLayoutPacket(
    val sessionId: Int,
    val layout: SlotLayout,
    val contents: List<ItemStack> = emptyList(),
    val carried: ItemStack = ItemStack.EMPTY,
    val revision: Int = 0,
) : HollowPacket {
    override fun handle(player: Player) = ClientSlots.open(sessionId, layout, contents, carried, revision)
}

/**
 * The authoritative contents of whatever moved, and the revision they are true at.
 *
 * [full] marks a resync: the client's prediction was refused or it acted on stale data, so [changes]
 * describes every slot rather than a difference.
 */
@HollowPacketHandler(HollowPacketHandler.Direction.TO_CLIENT)
@Serializable
class SlotSyncPacket(
    val sessionId: Int,
    val revision: Int,
    val changes: List<SlotChange> = emptyList(),
    val carried: ItemStack = ItemStack.EMPTY,
    val full: Boolean = false,
) : HollowPacket {
    override fun handle(player: Player) = ClientSlots.sync(sessionId, revision, changes, carried, full)
}

/**
 * A gesture the player made, quoting the revision it was predicted against. A mismatch means the client
 * acted on contents the server has already moved past, and it gets the truth instead.
 */
@HollowPacketHandler(HollowPacketHandler.Direction.TO_SERVER)
@Serializable
class SlotIntentPacket(
    val sessionId: Int,
    val revision: Int,
    val intent: SlotIntent,
) : HollowPacket {
    override fun handle(player: Player) = SlotContainers.onIntent(player, sessionId, revision, intent)
}
