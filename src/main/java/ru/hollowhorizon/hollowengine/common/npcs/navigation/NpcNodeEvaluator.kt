package ru.hollowhorizon.hollowengine.common.npcs.navigation

import net.minecraft.world.level.pathfinder.BlockPathTypes
import net.minecraft.world.level.pathfinder.Node
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator

class NpcNodeEvaluator : WalkNodeEvaluator() {
    init {
        setCanFloat(true)
        setCanOpenDoors(true)
        setCanPassDoors(true)
    }

    override fun isDiagonalValid(root: Node, xNode: Node?, zNode: Node?, diagonal: Node?): Boolean {
        if (diagonal == null || diagonal.closed || diagonal.type == BlockPathTypes.WALKABLE_DOOR) return false

        val isZNodeValid =
            zNode != null && !zNode.closed && zNode.type != BlockPathTypes.WALKABLE_DOOR && (zNode.y <= root.y || zNode.costMalus >= 0.0f)
        val isXNodeValid =
            xNode != null && !xNode.closed && xNode.type != BlockPathTypes.WALKABLE_DOOR && (xNode.y <= root.y || xNode.costMalus >= 0.0f)
        val isNarrowFence =
            zNode != null && xNode != null && zNode.type == BlockPathTypes.FENCE && xNode.type == BlockPathTypes.FENCE && mob.bbWidth.toDouble() < 0.5

        return diagonal.costMalus >= 0.0f && (isZNodeValid || isXNodeValid || isNarrowFence)
    }
}