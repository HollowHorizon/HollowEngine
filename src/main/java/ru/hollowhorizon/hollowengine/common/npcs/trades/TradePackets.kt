package ru.hollowhorizon.hollowengine.common.npcs.trades

import kotlinx.serialization.Serializable
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import ru.hollowhorizon.hc.common.utils.get
import ru.hollowhorizon.hc.common.utils.mcText
//? if <=1.19.2
/*import ru.hollowhorizon.hc.client.utils.math.level*/
import ru.hollowhorizon.hc.common.containers.ClientContainerManager
import ru.hollowhorizon.hc.common.containers.ServerContainerManager
import ru.hollowhorizon.hc.common.events.SubscribeEvent
import ru.hollowhorizon.hc.common.events.container.ContainerEvent
import ru.hollowhorizon.hc.common.network.HollowPacketHandler
import ru.hollowhorizon.hc.common.network.HollowPacket
import ru.hollowhorizon.hc.common.utils.literal
import ru.hollowhorizon.hollowengine.client.gui.npcs.trading.TradeMenuGui
import ru.hollowhorizon.hollowengine.common.entities.NpcEntity
import ru.hollowhorizon.hollowengine.common.npcs.NPCCapability
import ru.hollowhorizon.hollowengine.common.npcs.TradeContainer
import ru.hollowhorizon.hollowengine.common.npcs.TradeOffer
import ru.hollowhorizon.hollowengine.common.registry.ModItems
import ru.hollowhorizon.hollowengine.common.util.PlayerPermissions

@Serializable
@HollowPacketHandler(HollowPacketHandler.Direction.TO_SERVER)
class AddTradePacket(val npc: Int) : HollowPacket<AddTradePacket> {
    override fun handle(player: Player) {
        if (!player.hasPermissions(PlayerPermissions.GAMEMASTER)) {
            player.sendSystemMessage("You can't modify npc trades!".literal)
            return
        }
        (player.level().getEntity(npc) as? NpcEntity)?.let { npc ->
            npc[NPCCapability::class].trades.add(TradeOffer(ItemStack.EMPTY, Array(6) { ItemStack.EMPTY }))
        }
    }
}

@Serializable
@HollowPacketHandler(HollowPacketHandler.Direction.TO_SERVER)
class RemoveTradePacket(val npc: Int, val index: Int) : HollowPacket<AddTradePacket> {
    override fun handle(player: Player) {
        if (!player.hasPermissions(PlayerPermissions.GAMEMASTER)) {
            player.sendSystemMessage("You can't modify npc trades!".mcText)
            return
        }
        (player.level().getEntity(npc) as? NpcEntity)?.let { npc ->
            val trades = npc[NPCCapability::class].trades

            if (index !in 0..<trades.size) {
                player.sendSystemMessage("Invalid trade index!".mcText)
                return
            }
            trades.removeAt(index)
        }
    }
}

@Serializable
@HollowPacketHandler(HollowPacketHandler.Direction.TO_SERVER)
class ModifyTradePacket(val npc: Int, val index: Int, private val trade: TradeOffer) : HollowPacket<AddTradePacket> {
    override fun handle(player: Player) {
        if (!player.hasPermissions(PlayerPermissions.GAMEMASTER)) {
            player.sendSystemMessage("You can't modify npc trades!".mcText)
            return
        }
        (player.level().getEntity(npc) as? NpcEntity)?.let { npc ->
            val trades = npc[NPCCapability::class].trades

            if (index !in 0..<trades.size) {
                player.sendSystemMessage("Invalid trade index!".mcText)
                return
            }

            trades[index] = trade
        }
    }
}

@Serializable
@HollowPacketHandler(HollowPacketHandler.Direction.TO_SERVER)
class SelectTradePacket(val npc: Int, val index: Int) : HollowPacket<AddTradePacket> {
    override fun handle(player: Player) {
        if (!player.hasPermissions(PlayerPermissions.GAMEMASTER)) {
            player.sendSystemMessage("You can't modify npc trades!".mcText)
            return
        }
        (player.level().getEntity(npc) as? NpcEntity)?.let { npc ->
            val trades = npc[NPCCapability::class].trades

            if (index !in 0..<trades.size && index != -1) {
                player.sendSystemMessage("Invalid trade index!".mcText)
                return
            }

            npc[NPCCapability::class].currentTrade = index
        }
    }
}

@Serializable
@HollowPacketHandler(HollowPacketHandler.Direction.TO_SERVER)
class ClearContainerPacket(val npc: Int) : HollowPacket<AddTradePacket> {
    override fun handle(player: Player) {
        if (!player.hasPermissions(PlayerPermissions.GAMEMASTER)) {
            player.sendSystemMessage("You can't modify npc trades!".mcText)
            return
        }
        (player.level().getEntity(npc) as? NpcEntity)?.let { npc ->
            npc[NPCCapability::class].tradeContainer.clearContent()
        }
    }
}


@SubscribeEvent
fun onTakeTrade(event: ContainerEvent.OnTake) {
    if (event.container is TradeContainer && event.slot == 6) {
        val capability = event.container.capability as NPCCapability
        if (capability.currentTrade == -1) return
        val trade = capability.trades[capability.currentTrade]

        event.container.apply {
            trade.inputs.forEachIndexed { i, input ->
                getItem(i).shrink(input.count)
            }
        }
    }
}

@SubscribeEvent
fun onPlaceTrade(event: ContainerEvent.OnClick) {
    if (event.container is TradeContainer &&
        event.player.hasPermissions(PlayerPermissions.GAMEMASTER) &&
        event.player.mainHandItem.item == ModItems.NPC_TOOL
    ) {
        val manager = if (event.player.level().isClientSide) ClientContainerManager else ServerContainerManager

        event.isCanceled = true
        val holdItem = manager.PLAYERS_HOLD_STACKS[event.player.uuid]
        event.container.setItem(event.slot, holdItem?.copy() ?: ItemStack.EMPTY)

        if (event.player.level().isClientSide) {
            val screen = Minecraft.getInstance().screen as? TradeMenuGui ?: return
            if (screen.selectedTrade == -1) return

            val index = screen.page * 9 + screen.selectedTrade
            val trade = TradeOffer(
                event.container.getItem(6),
                //? if >=1.21 {
                /*event.container.items.subList(0, 6).toTypedArray(),
                *///?} else {
                (0 until event.container.size).map { event.container.getItem(it) }.toTypedArray()
                //?}
            )

            ModifyTradePacket(screen.npc.id, index, trade).send()
        }
    }
}