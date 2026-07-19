package ru.hollowhorizon.hollowengine.common.npcs.navigation

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.util.Mth
import net.minecraft.world.level.pathfinder.Node
import net.minecraft.world.level.pathfinder.PathType
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator

class NpcNodeEvaluator : WalkNodeEvaluator() {
    private val cardinalNeighbors = arrayOfNulls<Node>(Direction.Plane.HORIZONTAL.count())

    init {
        setCanFloat(true)
        setCanOpenDoors(true)
        setCanPassDoors(true)
    }

    override fun getNeighbors(outputArray: Array<Node>, node: Node): Int {
        var count = 0
        var stepHeight = 0
        val aboveType = getCachedPathType(node.x, node.y + 1, node.z)
        val currentType = getCachedPathType(node.x, node.y, node.z)
        if (mob.getPathfindingMalus(aboveType) >= 0.0f && currentType != PathType.STICKY_HONEY) {
            stepHeight = Mth.floor(maxOf(1.0f, mob.maxUpStep()))
        }

        val floorLevel = getFloorLevel(BlockPos(node.x, node.y, node.z))
        for (direction in Direction.Plane.HORIZONTAL) {
            val neighbor = findAcceptedNode(
                node.x + direction.stepX,
                node.y,
                node.z + direction.stepZ,
                stepHeight,
                floorLevel,
                direction,
                currentType,
            )
            cardinalNeighbors[direction.get2DDataValue()] = neighbor
            if (neighbor != null && isNeighborValid(neighbor, node)) outputArray[count++] = neighbor
        }

        for (direction in Direction.Plane.HORIZONTAL) {
            val clockwise = direction.clockWise
            val diagonalX = node.x + direction.stepX + clockwise.stepX
            val diagonalZ = node.z + direction.stepZ + clockwise.stepZ
            val vanillaDiagonal = findAcceptedNode(
                diagonalX,
                node.y,
                diagonalZ,
                stepHeight,
                floorLevel,
                direction,
                currentType,
            )
            val vanillaSidesValid = isDiagonalValid(
                node,
                cardinalNeighbors[direction.get2DDataValue()],
                cardinalNeighbors[clockwise.get2DDataValue()],
            )
            if (vanillaDiagonal != null && vanillaSidesValid && isDiagonalValid(vanillaDiagonal)) {
                outputArray[count++] = vanillaDiagonal
                continue
            }

            val diagonal = findIndependentDiagonal(
                node,
                diagonalX,
                diagonalZ,
                floorLevel,
                vanillaDiagonal,
            ) ?: continue
            outputArray[count++] = diagonal
        }
        return count
    }

    private fun findIndependentDiagonal(
        from: Node,
        x: Int,
        z: Int,
        fromFloor: Double,
        vanillaCandidate: Node?,
    ): Node? {
        if (vanillaCandidate != null &&
            isDiagonalValid(vanillaCandidate) &&
            canTraverseDiagonal(from, vanillaCandidate, fromFloor)
        ) {
            return vanillaCandidate
        }

        for (yOffset in DIAGONAL_Y_OFFSETS) {
            val y = from.y + yOffset
            if (vanillaCandidate?.x == x && vanillaCandidate.y == y && vanillaCandidate.z == z) continue

            val pathType = getCachedPathType(x, y, z)
            val malus = mob.getPathfindingMalus(pathType)
            if (malus < 0.0f || pathType == PathType.OPEN || pathType == PathType.WALKABLE_DOOR) continue

            val candidate = getNode(x, y, z)
            candidate.type = pathType
            candidate.costMalus = maxOf(candidate.costMalus, malus)
            if (isDiagonalValid(candidate) && canTraverseDiagonal(from, candidate, fromFloor)) {
                return candidate
            }
        }
        return null
    }

    private fun canTraverseDiagonal(from: Node, to: Node, fromFloor: Double): Boolean {
        val toFloor = getFloorLevel(BlockPos(to.x, to.y, to.z))
        val fromPosition = NpcNavigationGeometry.nodeCenter(mob, from.x, fromFloor, from.z)
        val toPosition = NpcNavigationGeometry.nodeCenter(mob, to.x, toFloor, to.z)
        return NpcNavigationGeometry.findWalkableWaypoint(
            currentContext.level(),
            mob,
            fromPosition,
            toPosition,
        ) != null || NpcNavigationGeometry.canJumpDirectly(
            currentContext.level(),
            mob,
            fromPosition,
            toPosition,
        ) || NpcNavigationGeometry.canDropDirectly(
            currentContext.level(),
            mob,
            fromPosition,
            toPosition,
        ) || NpcNavigationGeometry.canSqueezeDiagonally(
            currentContext.level(),
            mob,
            fromPosition,
            toPosition,
        )
    }

    internal fun additionalTravelCost(from: Node, to: Node): Float {
        val fromFloor = getFloorLevel(BlockPos(from.x, from.y, from.z))
        val toFloor = getFloorLevel(BlockPos(to.x, to.y, to.z))
        if (toFloor - fromFloor <= mob.maxUpStep() + HEIGHT_EPSILON) return 0.0f

        val surfacePos = BlockPos(to.x, to.y - 1, to.z)
        return if (NpcNavigationGeometry.hasStepableSurface(
                currentContext.level(),
                surfacePos,
                mob.maxUpStep().toDouble(),
                NpcNavigationGeometry.nodeCenter(mob, from.x, fromFloor, from.z),
            )
        ) {
            0.0f
        } else {
            JUMP_COST
        }
    }

    private companion object {
        const val HEIGHT_EPSILON = 1.0e-3
        const val JUMP_COST = 4.0f
        val DIAGONAL_Y_OFFSETS = intArrayOf(0, 1, -1)
    }
}
