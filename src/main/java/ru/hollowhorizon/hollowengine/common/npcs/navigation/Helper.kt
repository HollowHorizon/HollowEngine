package ru.hollowhorizon.hollowengine.common.npcs.navigation

import net.minecraft.core.BlockPos
import net.minecraft.tags.FluidTags
import net.minecraft.util.Mth
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

fun Vec3.sameBlock(other: Vec3) =
    Mth.floor(x) == Mth.floor(other.x) &&
            Mth.floor(y) == Mth.floor(other.y) &&
            Mth.floor(z) == Mth.floor(other.z)

val Vec3.blockX get() = Mth.floor(x)
val Vec3.blockY get() = Mth.floor(y)
val Vec3.blockZ get() = Mth.floor(z)
val Vec3.block get() = BlockPos(blockX, blockY, blockZ)

fun Entity.canFit(pos: BlockPos) = canFit(Vec3(pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble()))
fun Entity.canFit(vec: Vec3): Boolean {
    val box = boundingBox.move(vec.subtract(this.position()))
    return level().noCollision(box)
}

fun BlockGetter.getWaterAndLavaIn(box: AABB): Pair<Boolean, Boolean> {
    var hasWater = false
    var hasLava = false
    getBlockStates(box).forEach { state ->
        hasWater = hasWater or state.fluidState.`is`(FluidTags.WATER)
        hasLava = hasLava or state.fluidState.`is`(FluidTags.LAVA)
    }
    return hasWater to hasLava
}

fun BlockGetter.canJump(from: BlockPos, to: BlockPos): Boolean {
    // Разница по координатам
    val dx = abs(to.x - from.x)
    val dz = abs(to.z - from.z)
    val dy = to.y - from.y

    // Проверка горизонтального расстояния и высоты
    if (dx > 2 || dz > 2 || abs(dy) > 1) return false

    // Если нет движения по горизонтали, прыжок невозможен
    if (dx == 0 && dz == 0) return false

    // Проверка отсутствия препятствий
    val minX = min(from.x, to.x)
    val maxX = max(from.x, to.x)
    val minZ = min(from.z, to.z)
    val maxZ = max(from.z, to.z)

    // Проверяем все блоки на пути (включая диагональные)
    for (x in minX..maxX) {
        for (z in minZ..maxZ) {
            for (y in min(from.y, to.y)-1..max(from.y, to.y)) {
                val pos = BlockPos(x, y, z)
                if (pos.x == from.x && pos.z == from.z) continue
                if (pos.x == to.x && pos.z == to.z) continue
                if (!isPassable(pos)) return false
            }
        }
    }

    // Проверяем место приземления
    return isPassable(to) && getBlockState(to.below()).isSolid
}