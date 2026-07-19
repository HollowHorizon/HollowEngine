package ru.hollowhorizon.hollowengine.common.npcs.navigation

import net.minecraft.world.level.pathfinder.Node
import net.minecraft.world.level.pathfinder.PathFinder

class NpcPathFinder(
    private val evaluator: NpcNodeEvaluator,
    maxVisitedNodes: Int,
) : PathFinder(evaluator, maxVisitedNodes) {
    override fun distance(first: Node, second: Node): Float {
        return super.distance(first, second) + evaluator.additionalTravelCost(first, second)
    }
}

