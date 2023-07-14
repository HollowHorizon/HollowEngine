package ru.hollowhorizon.hollowengine.common.items

import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.util.ActionResult
import net.minecraft.util.Hand
import net.minecraft.world.World
import net.minecraft.world.server.ServerWorld


class StoryTellerDimItem : Item(Properties().stacksTo(1)) {
    override fun use(pLevel: World, pPlayer: PlayerEntity, pHand: Hand): ActionResult<ItemStack> {
        if(!pLevel.isClientSide && pHand == Hand.MAIN_HAND) {
            val serverWorld = pPlayer.commandSenderWorld as ServerWorld
        }
        return super.use(pLevel, pPlayer, pHand)
    }
}