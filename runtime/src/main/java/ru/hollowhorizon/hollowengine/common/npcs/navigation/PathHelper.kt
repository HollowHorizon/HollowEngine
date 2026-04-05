package ru.hollowhorizon.hollowengine.common.npcs.navigation

import net.minecraft.core.BlockPos
import net.minecraft.world.level.BlockGetter

fun BlockGetter.isPassable(pos: BlockPos): Boolean {
    val state = getBlockState(pos)
    return state.isAir || state.canBeReplaced()
}

fun BlockGetter.isSolid(pos: BlockPos) = getBlockState(pos).isSolid
fun BlockGetter.isAir(pos: BlockPos) = getBlockState(pos).isAir