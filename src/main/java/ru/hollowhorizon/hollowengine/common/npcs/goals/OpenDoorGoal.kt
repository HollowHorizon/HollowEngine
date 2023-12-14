package ru.hollowhorizon.hollowengine.common.npcs.goals

import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionHand.*
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.ai.goal.Goal
import net.minecraft.world.level.block.DoorBlock
import net.minecraft.world.level.pathfinder.Path
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import ru.hollowhorizon.hollowengine.common.entities.NPCEntity
import java.util.*


class OpenDoorGoal(private val entity: NPCEntity) : Goal() {
    private var stuckTime = 0
    private var lastPos = Vec3(0.0,0.0,0.0)

    override fun canUse(): Boolean {
        if(lastPos == entity.position()) stuckTime++

        if (!entity.navigation.isDone && stuckTime > 10) {
            stuckTime = 0
            return true
        }

        lastPos = entity.position()
        return false
    }

    override fun tick() {
        val lookBlock = entity.pick(2.5, 0f, false) as? BlockHitResult ?: return
        val state = entity.level.getBlockState(lookBlock.blockPos)
        if(DoorBlock.isWoodenDoor(state)) {
            entity.swing(MAIN_HAND)
            state.use(entity.level, entity.fakePlayer, MAIN_HAND, lookBlock)
        }
    }
}