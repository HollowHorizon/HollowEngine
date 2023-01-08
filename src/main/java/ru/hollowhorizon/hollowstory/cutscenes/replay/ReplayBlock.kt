package ru.hollowhorizon.hollowstory.cutscenes.replay

import kotlinx.serialization.Serializable
import net.minecraft.block.DoorBlock
import net.minecraft.entity.LivingEntity
import net.minecraft.item.BlockItem
import net.minecraft.item.ItemStack
import net.minecraft.item.ItemUseContext
import net.minecraft.server.management.PlayerInteractionManager
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.BlockRayTraceResult
import net.minecraft.world.World
import net.minecraft.world.server.ServerWorld
import net.minecraftforge.common.util.FakePlayer
import net.minecraftforge.registries.ForgeRegistries
import ru.hollowhorizon.hc.client.utils.nbt.ForBlockPos
import ru.hollowhorizon.hc.client.utils.toRL


@Serializable
class ReplayBlock(
    val pos: @Serializable(ForBlockPos::class) BlockPos,
    val block: String,
) {
    fun place(level: World, target: LivingEntity, fakePlayer: FakePlayer) {
        val block =
            ForgeRegistries.BLOCKS.getValue(block.toRL()) ?: throw IllegalArgumentException("Block $block not found")
        val blockItem = block.asItem()

        if (blockItem is BlockItem) {
            blockItem.useOn(ItemUseContext(fakePlayer, target.usedItemHand, BlockRayTraceResult(target.lookAngle, target.direction, pos, false)))
        } else {
            level.setBlockAndUpdate(pos, block.defaultBlockState())

            if (block is DoorBlock) {
                block.setPlacedBy(level, pos, block.defaultBlockState(), null, ItemStack.EMPTY)
            }
        }
    }

    fun placeWorld(level: World) {
        val block =
            ForgeRegistries.BLOCKS.getValue(block.toRL()) ?: throw IllegalArgumentException("Block $block not found")
        level.setBlockAndUpdate(pos, block.defaultBlockState())
    }

    fun destroy(fakePlayer: FakePlayer) {
        val manager = fakePlayer.gameMode

        manager.destroyBlock(pos)
    }

    fun destroyWorld(level: World) {
        level.destroyBlock(pos, false)
    }

    fun use(level: World, target: LivingEntity, fakePlayer: FakePlayer) {
        val manager = fakePlayer.gameMode

        manager.useItemOn(fakePlayer, level, target.useItem, target.usedItemHand, BlockRayTraceResult(target.lookAngle, target.direction, pos, false))
    }
}