package ru.hollowhorizon.hollowengine.common.npcs.navigation

import dev.folomeev.kotgl.matrix.vectors.Vec3
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.util.Mth
import net.minecraft.world.level.pathfinder.BlockPathTypes
import net.minecraft.world.level.pathfinder.Node
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator
import kotlin.math.max

class HollowNodeEvaluator : WalkNodeEvaluator() {
    init {
        setCanFloat(true)
        setCanOpenDoors(true)
        setCanPassDoors(true)
    }

    override fun getNeighbors(outputArray: Array<Node>, node: Node): Int {
        var i = super.getNeighbors(outputArray, node)
        val source = this.getCachedBlockType(this.mob, node.x, node.y, node.z)
        val upper = this.getCachedBlockType(this.mob, node.x, node.y + 1, node.z)
        val canJumpUp = mob.getPathfindingMalus(upper) >= 0.0f && source != BlockPathTypes.STICKY_HONEY
        val maxStepHeight = if (canJumpUp) Mth.floor(max(1.0, mob.maxUpStep().toDouble())) else 0
        val d = this.getFloorLevel(BlockPos(node.x, node.y, node.z))

        val directions = listOf(
            Direction.EAST to BlockPos(2, 0, 0),
            Direction.EAST to BlockPos(2, 1, 1),
            Direction.EAST to BlockPos(2, 1, -1),

            Direction.WEST to BlockPos(-2, 0, 0),
            Direction.WEST to BlockPos(-2, 1, 1),
            Direction.WEST to BlockPos(-2, 1, -1),

            Direction.SOUTH to BlockPos(0, 0, 2),
            Direction.SOUTH to BlockPos(1, 1, 2),
            Direction.SOUTH to BlockPos(-1, 1, 2),

            Direction.NORTH to BlockPos(0, 0, -2),
            Direction.NORTH to BlockPos(1, 1, -2),
            Direction.NORTH to BlockPos(-1, 1, -2),

            Direction.EAST to BlockPos(3, 0, 0),
            Direction.EAST to BlockPos(3, 1, 1),
            Direction.EAST to BlockPos(3, 1, -1),

            Direction.WEST to BlockPos(-3, 0, 0),
            Direction.WEST to BlockPos(-3, 1, 1),
            Direction.WEST to BlockPos(-3, 1, -1),

            Direction.SOUTH to BlockPos(0, 0, 3),
            Direction.SOUTH to BlockPos(1, 1, 3),
            Direction.SOUTH to BlockPos(-1, 1, 3),

            Direction.NORTH to BlockPos(0, 0, -3),
            Direction.NORTH to BlockPos(1, 1, -3),
            Direction.NORTH to BlockPos(-1, 1, -3),
        )

        directions.forEach { (dir, offset) ->
            val jump = findAcceptedNode(node.x + offset.x, node.y + offset.y, node.z + offset.z, maxStepHeight, d, dir, source)
            if (isNeighborValid(jump, node) && level.canJump(node.asBlockPos(), jump!!.asBlockPos())) {
                outputArray[i++] = JumpNode.fromNode(jump)
            }
        }

        return i
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