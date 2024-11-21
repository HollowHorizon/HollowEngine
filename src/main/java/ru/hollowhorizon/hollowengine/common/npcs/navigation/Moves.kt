package ru.hollowhorizon.hollowengine.common.npcs.navigation

import net.minecraft.core.BlockPos
import net.minecraft.tags.BlockTags
import net.minecraft.world.level.Level
import kotlin.math.sqrt


enum class Moves(val x: Int, val y: Int, val z: Int) {
    DOWNWARD(0, -1, 0),
    PILLAR(0, +1, 0),
    TRAVERSE_NORTH(0, 0, -1),
    TRAVERSE_SOUTH(0, 0, +1),
    TRAVERSE_EAST(+1, 0, 0),
    TRAVERSE_WEST(-1, 0, 0),
    ASCEND_NORTH(0, +1, -1),
    ASCEND_SOUTH(0, +1, +1),
    ASCEND_EAST(+1, +1, 0),
    ASCEND_WEST(-1, +1, 0),
    DESCEND_EAST(+1, -1, 0),
    DESCEND_WEST(-1, -1, 0),
    DESCEND_NORTH(0, -1, -1),
    DESCEND_SOUTH(0, -1, +1),
    DIAGONAL_NORTHEAST(+1, 0, -1),
    DIAGONAL_NORTHWEST(-1, 0, -1),
    DIAGONAL_SOUTHEAST(+1, 0, +1),
    DIAGONAL_SOUTHWEST(-1, 0, +1),
    PARKOUR_NORTH(0, 0, -3),
    PARKOUR_SOUTH(0, 0, +3),
    PARKOUR_EAST(+3, 0, 0),
    PARKOUR_WEST(-3, 0, 0);

    val pos get() = BlockPos(x, y, z)

    fun cost(level: Level, target: BlockPos): Double {
        val targetPos = target.offset(pos)

        if(!level.isPassable(targetPos)) return Double.MAX_VALUE

        when(this) {
            PILLAR -> {
                val below = targetPos.below()
                if (!level.isSolid(below)) return Double.MAX_VALUE
            }
            else -> {}
        }

        return sqrt((x * x + y * y + z * z).toDouble())
    }
}