package ru.hollowhorizon.hollowengine.common.items

import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.context.UseOnContext
import ru.hollowhorizon.hc.client.utils.rl
import ru.hollowhorizon.hollowengine.common.registry.ModDimensions
import ru.hollowhorizon.hollowengine.common.tabs.HOLLOWENGINE_TAB

class StoryTellerDimItem : Item(Properties().stacksTo(1).tab(HOLLOWENGINE_TAB)) {
    override fun onItemUseFirst(stack: ItemStack, context: UseOnContext): InteractionResult {
        if (!context.level.isClientSide && context.hand == InteractionHand.MAIN_HAND) {
            val player = context.player as? ServerPlayer ?: return InteractionResult.PASS
            val serverWorld = player.commandSenderWorld as? ServerLevel ?: return InteractionResult.PASS
            if (serverWorld.dimension() == ModDimensions.STORYTELLER_DIMENSION) {
                var posX = 0.0
                var posY = 0.0
                var posZ = 0.0
                var xRot = 0f
                var yRot = 0f
                var oldDim = "minecraft:overworld"
                stack.orCreateTag.apply {
                    if (contains("pos_x")) {
                        posX = getDouble("pos_x")
                        posY = getDouble("pos_y")
                        posZ = getDouble("pos_z")

                        xRot = getFloat("view_x")
                        yRot = getFloat("view_y")
                        oldDim = getString("old_dim")
                    }
                }

                val newDim = serverWorld.server.levelKeys().find { it.location().equals(oldDim.rl) }
                    ?: serverWorld.server.overworld().dimension()
                player.teleportTo(
                    serverWorld.server.getLevel(newDim) ?: return InteractionResult.PASS,
                    posX,
                    posY,
                    posZ,
                    yRot,
                    xRot
                )
                return InteractionResult.SUCCESS
            } else {
                stack.orCreateTag.apply {
                    putDouble("pos_x", player.x)
                    putDouble("pos_y", player.y)
                    putDouble("pos_z", player.z)

                    putFloat("view_x", player.xRot)
                    putFloat("view_y", player.yHeadRot)
                    putString("old_dim", serverWorld.dimension().location().toString())
                }

                player.teleportTo(
                    serverWorld.server.getLevel(ModDimensions.STORYTELLER_DIMENSION) ?: return InteractionResult.PASS,
                    0.5,
                    50.0,
                    0.5,
                    0f,
                    0f
                )
                return InteractionResult.SUCCESS
            }
        }
        return super.onItemUseFirst(stack, context)
    }
}