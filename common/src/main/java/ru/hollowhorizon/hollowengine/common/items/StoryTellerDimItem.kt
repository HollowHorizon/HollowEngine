package ru.hollowhorizon.hollowengine.common.items

import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResultHolder
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import ru.hollowhorizon.hc.common.handlers.tab
import ru.hollowhorizon.hollowengine.common.registry.ModDimensions
import ru.hollowhorizon.hollowengine.common.registry.ModTabs

class StoryTellerDimItem : Item(Properties().stacksTo(1)) {
    init {
        tab(ModTabs.HOLLOW_ENGINE.get())
    }

    override fun use(level: Level, player: Player, hand: InteractionHand): InteractionResultHolder<ItemStack> {
        val stack = player.getItemInHand(hand)
        if (!level.isClientSide && hand == InteractionHand.MAIN_HAND) {
            val serverPlayer = player as? ServerPlayer ?: return InteractionResultHolder.pass(stack)
            val serverWorld =
                serverPlayer.commandSenderWorld as? ServerLevel ?: return InteractionResultHolder.pass(stack)
            if (serverWorld.dimension() == ModDimensions.STORYTELLER_DIMENSION) {
                val newDim = serverWorld.server.overworld().dimension()
                val pos = player.respawnPosition ?: serverWorld.sharedSpawnPos
                serverPlayer.teleportTo(
                    serverWorld.server.getLevel(newDim) ?: return InteractionResultHolder.pass(stack),
                    pos.x.toDouble(),
                    pos.y.toDouble(),
                    pos.z.toDouble(),
                    0f,
                    0f
                )
                return InteractionResultHolder.success(stack)
            } else {
                serverPlayer.teleportTo(
                    serverWorld.server.getLevel(ModDimensions.STORYTELLER_DIMENSION)
                        ?: return InteractionResultHolder.pass(stack),
                    0.5,
                    50.0,
                    0.5,
                    0f,
                    0f
                )
                return InteractionResultHolder.success(stack)
            }
        }
        return super.use(level, player, hand)
    }
}