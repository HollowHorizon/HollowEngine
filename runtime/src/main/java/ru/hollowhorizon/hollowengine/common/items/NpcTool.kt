package ru.hollowhorizon.hollowengine.common.items

import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.InteractionResultHolder
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.level.Level
import ru.hollowhorizon.hollowengine.client.ui.entity.EntityEditorClient
import ru.hollowhorizon.hollowengine.common.attachments.api.set
import ru.hollowhorizon.hollowengine.common.attachments.components.Model
import ru.hollowhorizon.hollowengine.common.attachments.editor.canEditEntities
import ru.hollowhorizon.hollowengine.common.entities.NpcEntity
import ru.hollowhorizon.hollowengine.common.events.SubscribeEvent
import ru.hollowhorizon.hollowengine.common.events.entity.player.PlayerInteractEvent
import ru.hollowhorizon.hollowengine.common.objects.items.CreativeTab
import ru.hollowhorizon.hollowengine.common.registry.ModItems
import ru.hollowhorizon.hollowengine.common.registry.ModTabs
import net.minecraft.network.chat.Component as ChatComponent

class NpcTool : Item(Properties().stacksTo(1)), CreativeTab {

    override fun use(level: Level, player: Player, hand: InteractionHand): InteractionResultHolder<ItemStack> {
        val stack = player.getItemInHand(hand)
        if (hand != InteractionHand.MAIN_HAND || !player.isShiftKeyDown) {
            return InteractionResultHolder.pass(stack)
        }
        if (level.isClientSide) openEditor(player, player)
        return InteractionResultHolder.success(stack)
    }

    override fun useOn(context: UseOnContext): InteractionResult {
        val player = context.player ?: return InteractionResult.PASS
        val level = context.level
        if (context.hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS

        if (player.isShiftKeyDown) {
            if (level.isClientSide) openEditor(player, player)
            return InteractionResult.sidedSuccess(level.isClientSide)
        }
        if (!player.canEditEntities()) {
            if (level.isClientSide) denied(player)
            return InteractionResult.FAIL
        }

        if (!level.isClientSide) {
            val pos = context.clickedPos.relative(context.clickedFace)
            val npc = NpcEntity(level)
            npc.moveTo(pos.x + 0.5, pos.y.toDouble(), pos.z + 0.5, player.yRot + 180f, 0f)
            npc set Model()
            level.addFreshEntity(npc)
        }
        return InteractionResult.sidedSuccess(level.isClientSide)
    }

    override fun tab() = ModTabs.HOLLOW_ENGINE
}

@SubscribeEvent
fun PlayerInteractEvent.EntityInteract.onInteract() {
    if (hand != InteractionHand.MAIN_HAND) return
    if (player.mainHandItem.item != ModItems.NPC_TOOL) return

    isCanceled = true
    if (!player.level().isClientSide) return
    openEditor(player, if (player.isShiftKeyDown) player else target)
}

private fun openEditor(player: Player, target: Entity) {
    if (!player.canEditEntities()) {
        denied(player)
        return
    }
    EntityEditorClient.request(target)
}

private fun denied(player: Player) {
    player.displayClientMessage(ChatComponent.translatable("hollowengine.gui.entity_editor.no_permission"), true)
}
