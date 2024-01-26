package ru.hollowhorizon.hollowengine.common.items

import net.minecraft.client.Minecraft
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import ru.hollowhorizon.hollowengine.client.screen.npcs.ModelEditScreen
import ru.hollowhorizon.hollowengine.common.entities.NPCEntity
import ru.hollowhorizon.hollowengine.common.tabs.HOLLOWENGINE_TAB

class NpcTool : Item(Properties().tab(HOLLOWENGINE_TAB).stacksTo(1)) {
    override fun interactLivingEntity(
        pStack: ItemStack,
        pPlayer: Player,
        pInteractionTarget: LivingEntity,
        pUsedHand: InteractionHand
    ): InteractionResult {
        if(pUsedHand == InteractionHand.MAIN_HAND && pPlayer.level.isClientSide && pInteractionTarget is NPCEntity) {
            Minecraft.getInstance().setScreen(ModelEditScreen(pInteractionTarget))
        }

        return super.interactLivingEntity(pStack, pPlayer, pInteractionTarget, pUsedHand)
    }
}