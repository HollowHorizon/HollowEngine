package ru.hollowhorizon.hollowengine.common.npcs.goals

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.sounds.SoundSource
import net.minecraft.tags.FluidTags
import net.minecraft.util.Mth
import net.minecraft.world.InteractionHand
import net.minecraft.world.effect.MobEffectUtil
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.ai.goal.Goal
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.enchantment.EnchantmentHelper
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Rotation
import net.minecraft.world.level.block.SoundType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.Vec3
import kotlin.math.abs


class BlockBreakGoal(val living: Mob) : Goal() {
    private var target: LivingEntity? = null
    private var markedLoc: BlockPos? = null
    private var entityPos: BlockPos? = null
    private var digTimer = 0
    private var cooldown: Int = 40
    private val breakAOE: MutableList<BlockPos> = ArrayList()
    private var breakIndex = 0
    private val digHeight: Int

    init {
        val digWidth = if (living.bbWidth < 1) 0 else Mth.ceil(living.bbWidth)
        digHeight = living.bbHeight.toInt() + 1
        for (i in digHeight downTo 0) breakAOE.add(BlockPos(0, i, 0))

        for (z in digWidth + 1 downTo -digWidth) for (y in digHeight downTo 0) {
            for (x in 0..digWidth) {
                if (z != 0) {
                    breakAOE.add(BlockPos(x, y, z))
                    if (x != 0) breakAOE.add(BlockPos(-x, y, z))
                }
            }
        }
    }

    override fun canUse(): Boolean {
        target = living.target ?: return false
        if (entityPos == null) {
            entityPos = living.blockPosition()
            cooldown = 20
        }
        if (--cooldown <= 0) {
            if (entityPos != living.blockPosition()) {
                entityPos = null
                cooldown = 40
                return false
            } else if (target != null && living.distanceTo(target!!) > 1.0) {
                val blockPos = diggingLocation ?: return false
                cooldown = 40
                markedLoc = blockPos
                entityPos = living.blockPosition()
                return true
            }
        }
        return false
    }

    override fun canContinueToUse(): Boolean {
        return target != null && target!!.isAlive && living.isAlive && markedLoc != null && nearSameSpace(
            entityPos,
            living.blockPosition()
        ) && living.distanceTo(target!!) > 1.0
    }

    private fun nearSameSpace(pos1: BlockPos?, pos2: BlockPos?): Boolean {
        return pos1 != null && pos2 != null && pos1.x == pos2.x && pos1.z == pos2.z && abs((pos1.y - pos2.y).toDouble()) <= 1
    }

    override fun stop() {
        breakIndex = 0
        if (markedLoc != null) living.level.destroyBlockProgress(living.id, markedLoc!!, -1)
        markedLoc = null
    }

    override fun requiresUpdateEveryTick(): Boolean {
        return true
    }

    override fun tick() {
        if (markedLoc == null || living.level.getBlockState(markedLoc!!)
                .getCollisionShape(living.level, markedLoc!!).isEmpty
        ) {
            digTimer = 0
            return
        }
        val state = living.level.getBlockState(markedLoc!!)
        var str: Float = getBlockStrength(living, state, living.level, markedLoc!!)
        str = if (str == Float.POSITIVE_INFINITY) 1f else str / (1 + str * 6) * (digTimer + 1)
        if (str >= 1f) {
            digTimer = 0
            cooldown = (cooldown * 0.5).toInt()
            val item = living.mainHandItem
            val itemOff = living.offhandItem
            val canHarvest = canHarvest(state, item) || canHarvest(state, itemOff)
            living.level.destroyBlock(markedLoc!!, canHarvest)
            markedLoc = null
            if (!aboveTarget()) {
                living.setSpeed(0f)
                living.navigation.stop()
                living.navigation.moveTo(living.navigation.createPath(target!!, 0), 1.0)
            }
        } else {
            digTimer++
            if (digTimer % 5 == 0) {
                val sound: SoundType = state.getSoundType(living.level, markedLoc!!, living)
                living.level.playSound(
                    null,
                    markedLoc!!.x + 0.5,
                    markedLoc!!.y + 0.5,
                    markedLoc!!.z + 0.5,
                    sound.breakSound,
                    SoundSource.BLOCKS,
                    2f,
                    0.5f
                )
                living.swing(InteractionHand.MAIN_HAND)
                living.lookControl.setLookAt(
                    markedLoc!!.x.toDouble(),
                    markedLoc!!.y.toDouble(),
                    markedLoc!!.z.toDouble(),
                    0.0f,
                    0.0f
                )
                living.level.destroyBlockProgress(living.id, markedLoc!!, str.toInt() * digTimer * 10)
            }
        }
    }

    val diggingLocation: BlockPos?
        get() {
            val item = living.mainHandItem
            val itemOff = living.offhandItem
            var pos = living.blockPosition()
            var state: BlockState
            if (living.target != null) {
                val target = living.target!!.position()
                if (aboveTarget() && abs(target.x - pos.x) <= 1 && abs(target.z - pos.z) <= 1) {
                    pos = living.blockPosition().below()
                    state = living.level.getBlockState(pos)
                    if (canBreak(state, item, itemOff)) {
                        breakIndex = 0
                        return pos
                    }
                }
            }
            val rot = getDigDirection(living)
            var offset = breakAOE[breakIndex]
            offset = BlockPos(offset.x, if (aboveTarget()) -(offset.y - digHeight) else offset.y, offset.z)
            pos = pos.offset(offset.rotate(rot))
            state = living.level.getBlockState(pos)
            if (canBreak(state, item, itemOff)) {
                breakIndex = 0
                return pos
            }
            breakIndex++
            if (breakIndex == breakAOE.size) breakIndex = 0
            return null
        }

    private fun canBreak(
        state: BlockState,
        item: ItemStack,
        itemOff: ItemStack
    ): Boolean {
        return canHarvest(state, item) || canHarvest(state, itemOff)
    }

    private fun aboveTarget(): Boolean {
        return target!!.y < living.y + 1.1
    }

    companion object {
        fun getDigDirection(mob: Mob): Rotation {
            val path = mob.navigation.path
            if (path != null) {
                val point = if (path.nextNodeIndex < path.nodeCount) path.nextNode else null
                if (point != null) {
                    val dir = Vec3(point.x + 0.5, mob.position().y, point.z + 0.5).subtract(mob.position())
                    return if (abs(dir.x) < abs(dir.z)) {
                        if (dir.z >= 0) Rotation.NONE else Rotation.CLOCKWISE_180
                    } else {
                        if (dir.x > 0) Rotation.COUNTERCLOCKWISE_90 else Rotation.CLOCKWISE_90
                    }
                }
            }
            return when (mob.direction) {
                Direction.SOUTH -> Rotation.CLOCKWISE_180
                Direction.EAST -> Rotation.CLOCKWISE_90
                Direction.WEST -> Rotation.COUNTERCLOCKWISE_90
                else -> Rotation.NONE
            }
        }
    }
}

fun canHarvest(block: BlockState, item: ItemStack): Boolean {
    return item.isCorrectToolForDrops(block) || !block.requiresCorrectToolForDrops()
}

fun getBlockStrength(entityLiving: Mob, state: BlockState, world: Level, pos: BlockPos): Float {
    val hardness: Float = world.getBlockState(pos).getDestroySpeed(world, pos)
    if (hardness < 0) {
        return 0.0f
    }
    val main = entityLiving.mainHandItem
    val off = entityLiving.offhandItem
    return if (canHarvest(state, main)) {
        var speed = getBreakSpeed(entityLiving, main, state)
        if (canHarvest(state, off)) {
            val offSpeed = getBreakSpeed(entityLiving, off, state)
            if (offSpeed > speed) speed = offSpeed
        }
        speed / hardness / 30f
    } else if (canHarvest(state, off)) {
        getBreakSpeed(entityLiving, off, state) / hardness / 30f
    } else {
        getBreakSpeed(entityLiving, main, state) / hardness / 100f
    }
}

fun getBreakSpeed(entity: Mob, stack: ItemStack, state: BlockState): Float {
    var f = stack.getDestroySpeed(state)
    if (f > 1.0f) {
        val i = EnchantmentHelper.getBlockEfficiency(entity)
        val itemstack = entity.mainHandItem
        if (i > 0 && !itemstack.isEmpty) f += (i * i + 1).toFloat()
    }
    if (MobEffectUtil.hasDigSpeed(entity)) f *= 1.0f + (MobEffectUtil.getDigSpeedAmplification(entity) + 1) * 0.2f
    if (entity.hasEffect(MobEffects.DIG_SLOWDOWN)) {
        f *= when (entity.getEffect(MobEffects.DIG_SLOWDOWN)!!.amplifier) {
            0 -> 0.3f
            1 -> 0.09f
            2 -> 0.0027f
            else -> 8.1E-4f
        }
    }
    if (entity.isEyeInFluid(FluidTags.WATER) && !EnchantmentHelper.hasAquaAffinity(entity)) f /= 5.0f
    if (!entity.isOnGround) f /= 5.0f
    return f
}
