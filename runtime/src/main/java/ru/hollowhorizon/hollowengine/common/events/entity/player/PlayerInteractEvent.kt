package ru.hollowhorizon.hollowengine.common.events.entity.player

import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.BlockHitResult
import ru.hollowhorizon.hollowengine.common.events.Cancellable
import ru.hollowhorizon.hollowengine.common.events.factory.EventHandler
import ru.hollowhorizon.hollowengine.common.scripting.katari.binding.ScriptBinding

abstract class PlayerInteractEvent(player: Player) : PlayerEvent(player), Cancellable {
    override var isCanceled: Boolean = false

    @ScriptBinding
    class EntityInteract(player: Player, val hand: InteractionHand, val target: Entity) : PlayerInteractEvent(player) {
        companion object: EventHandler<EntityInteract>()
    }
    class BlockInteract(player: Player, val hand: InteractionHand, val state: BlockHitResult) : PlayerInteractEvent(player) {
        companion object: EventHandler<BlockInteract>()
    }
    @ScriptBinding
    class ItemInteract(player: Player, val hand: InteractionHand, val itemStack: ItemStack) : PlayerInteractEvent(player) {
        companion object: EventHandler<ItemInteract>()
    }
}
