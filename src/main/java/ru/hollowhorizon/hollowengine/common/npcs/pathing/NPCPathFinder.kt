package ru.hollowhorizon.hollowengine.common.npcs.pathing

import net.minecraft.core.BlockPos
import net.minecraft.util.Mth
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.Mob
import net.minecraft.world.level.PathNavigationRegion
import net.minecraft.world.level.pathfinder.Node
import net.minecraft.world.level.pathfinder.NodeEvaluator
import net.minecraft.world.level.pathfinder.Path
import net.minecraft.world.level.pathfinder.PathFinder
import net.minecraft.world.phys.Vec3
import kotlin.math.sqrt


class NPCPathFinder(processor: NodeEvaluator, maxVisitedNodes: Int) : PathFinder(processor, maxVisitedNodes) {
    override fun findPath(
        regionIn: PathNavigationRegion, mob: Mob, targetPositions: Set<BlockPos>, maxRange: Float,
        accuracy: Int, searchDepthMultiplier: Float
    ): Path? {
        val path = super.findPath(regionIn, mob, targetPositions, maxRange, accuracy, searchDepthMultiplier)
        return if (path == null) null else PatchedPath(path)
    }

    internal class PatchedPath(original: Path) : Path(
        copyPathPoints(original), original.target, original.canReach()
    ) {
        override fun getEntityPosAtNode(entity: Entity, index: Int): Vec3 {
            val point = getNode(index)
            val d0 = point.x + Mth.floor(entity.bbWidth + 1.0f) * 0.5
            val d1 = point.y.toDouble()
            val d2 = point.z + Mth.floor(entity.bbWidth + 1.0f) * 0.5
            return Vec3(d0, d1, d2)
        }

        companion object {
            private fun copyPathPoints(original: Path): List<Node> {
                val points = ArrayList<Node>()
                for (i in 0 until original.nodeCount) points.add(original.getNode(i))
                return points
            }
        }
    }
}