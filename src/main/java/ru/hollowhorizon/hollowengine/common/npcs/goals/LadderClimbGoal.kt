package ru.hollowhorizon.hollowengine.common.npcs.goals

import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.ai.goal.Goal
import net.minecraft.world.level.pathfinder.Path
import java.util.*


class LadderClimbGoal(private val entity: Mob) : Goal() {
    private var path: Path? = null

    init {
        setFlags(EnumSet.of(Flag.MOVE))
    }

    override fun canUse(): Boolean {
        if (!entity.navigation.isDone) {
            path = entity.navigation.path
            return path != null && entity.onClimbable()
        }
        return false
    }

    override fun tick() {
        val path = path ?: return
        val i: Int = path.nextNodeIndex
        if (i + 1 < path.nodeCount) {
            val y: Int = path.getNode(i).y
            val pointNext = path.getNode(i + 1)
            val down = entity.level.getBlockState(entity.blockPosition().below())
            val yMotion = if (pointNext.y < y || (pointNext.y == y && down.isLadder(
                    entity.level,
                    entity.blockPosition().below(),
                    entity
                ))
            ) -0.15 else 0.15
            entity.deltaMovement = entity.deltaMovement.multiply(0.1, 1.0, 0.1)
            entity.deltaMovement = entity.deltaMovement.add(0.0, yMotion, 0.0)
        }
    }
}