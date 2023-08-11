package ru.hollowhorizon.hollowengine.common.items

import net.minecraft.server.level.ServerLevel
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.context.UseOnContext


class StoryTellerDimItem : Item(Properties().stacksTo(1)) {
    override fun onItemUseFirst(stack: ItemStack, context: UseOnContext): InteractionResult {
        if(!context.level.isClientSide && context.hand == InteractionHand.MAIN_HAND) {
            val serverWorld = context.player?.commandSenderWorld as? ServerLevel ?: return InteractionResult.PASS
        }
        return super.onItemUseFirst(stack, context)
    }
}