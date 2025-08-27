package ru.hollowhorizon.hc.common.events.entity.player

import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.BlockHitResult
import ru.hollowhorizon.hc.common.events.Cancelable

abstract class PlayerInteractEvent(player: Player) : PlayerEvent(player), Cancelable {
    override var isCanceled: Boolean = false

    class EntityInteract(player: Player, val hand: InteractionHand, val target: Entity) : PlayerInteractEvent(player)
    class BlockInteract(player: Player, val hand: InteractionHand, val state: BlockHitResult) : PlayerInteractEvent(player)
    class ItemInteract(player: Player, val hand: InteractionHand, val itemStack: ItemStack) : PlayerInteractEvent(player)
}