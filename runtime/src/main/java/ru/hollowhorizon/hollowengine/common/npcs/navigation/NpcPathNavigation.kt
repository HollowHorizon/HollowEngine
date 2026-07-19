package ru.hollowhorizon.hollowengine.common.npcs.navigation

import net.minecraft.core.BlockPos
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.DoorBlock
import net.minecraft.world.level.pathfinder.Path
import net.minecraft.world.level.pathfinder.PathFinder
import net.minecraft.world.phys.Vec3
import ru.hollowhorizon.hollowengine.common.network.sendTrackingEntity
import ru.hollowhorizon.hollowengine.common.utils.isProduction
import kotlin.math.min
import net.minecraft.world.level.pathfinder.PathType as BlockPathTypes

class NpcPathNavigation(level: Level, mob: Mob) : GroundPathNavigation(mob, level) {
    private val openedDoors = mutableMapOf<BlockPos, Boolean>()
    private var steeringTarget: Vec3? = null

    override fun createPathFinder(maxVisitedNodes: Int): PathFinder {
        val evaluator = NpcNodeEvaluator()
        nodeEvaluator = evaluator
        return NpcPathFinder(evaluator, maxVisitedNodes)
    }

    override fun tick() {
        steeringTarget = null
        super.tick()
        closePassedDoors()
        val currentPath = path?.takeUnless(Path::isDone) ?: return
        val node = currentPath.nextNode

        if (node.type == BlockPathTypes.WALKABLE_DOOR) {
            val state = level.getBlockState(node.asBlockPos())
            if (DoorBlock.isWoodenDoor(state)) {
                val door = state.block as DoorBlock
                door.setOpen(mob, level, state, node.asBlockPos(), true)
                openedDoors.putIfAbsent(node.asBlockPos(), false)
            }
        }

        updateSteering(currentPath)
        if (steeringTarget == null) {
            val nodePosition = currentPath.getNextEntityPos(mob)
            steeringTarget = Vec3(nodePosition.x, getGroundY(nodePosition), nodePosition.z)
        }
        sendDebugPath(currentPath)
    }

    override fun canMoveDirectly(from: Vec3, to: Vec3): Boolean {
        if (!mob.onGround()) return false
        val groundedFrom = Vec3(from.x, mob.y, from.z)
        val groundedTo = Vec3(to.x, getGroundY(to), to.z)
        return NpcNavigationGeometry.canWalkDirectly(level, mob, groundedFrom, groundedTo)
    }

    private fun updateSteering(currentPath: Path) {
        if (!mob.onGround()) return

        val start = Vec3(mob.x, mob.y, mob.z)
        val firstIndex = currentPath.nextNodeIndex
        val lastIndex = min(firstIndex + MAX_LOOKAHEAD_NODES, currentPath.nodeCount - 1)
        for (index in lastIndex downTo firstIndex + 1) {
            if (!canSteerPast(currentPath, firstIndex, index)) continue

            val nodePosition = currentPath.getEntityPosAtNode(mob, index)
            val target = Vec3(nodePosition.x, getGroundY(nodePosition), nodePosition.z)
            if (!NpcNavigationGeometry.canWalkDirectly(level, mob, start, target)) continue

            moveToward(target)
            return
        }

        if (!canCutCorner(currentPath.nextNode.type)) return
        val nodePosition = currentPath.getNextEntityPos(mob)
        val target = Vec3(nodePosition.x, getGroundY(nodePosition), nodePosition.z)
        val waypoint = NpcNavigationGeometry.findWalkableWaypoint(level, mob, start, target)
            ?: NpcNavigationGeometry.findSqueezeWaypoint(level, mob, start, target)
            ?: return
        moveToward(waypoint)
    }

    private fun moveToward(target: Vec3) {
        steeringTarget = target
        mob.moveControl.setWantedPosition(target.x, target.y, target.z, speedModifier)
    }

    private fun sendDebugPath(currentPath: Path) {
        if (isProduction || level.isClientSide || mob.tickCount % DEBUG_SYNC_INTERVAL != 0) return
        val steeringTarget = steeringTarget ?: return
        val target = currentPath.target
        NpcPathDebugPacket(
            mob.id,
            List(currentPath.nodeCount) { index ->
                val node = currentPath.getNode(index)
                NpcPathDebugNode(node.x, node.y, node.z, node.type.name, node.costMalus)
            },
            currentPath.nextNodeIndex,
            target.x,
            target.y,
            target.z,
            currentPath.canReach(),
            NpcPathDebugPoint(steeringTarget.x, steeringTarget.y, steeringTarget.z),
        ).sendTrackingEntity(mob)
    }

    private fun canSteerPast(currentPath: Path, fromIndex: Int, toIndex: Int): Boolean {
        for (index in fromIndex..toIndex) {
            if (!canCutCorner(currentPath.getNode(index).type)) return false
        }
        return true
    }

    private fun closePassedDoors() {
        val iterator = openedDoors.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val distance = mob.position().distanceToSqr(entry.key.center)
            if (distance <= DOOR_NEAR_DISTANCE_SQ) {
                entry.setValue(true)
            } else if (entry.value) {
                closeDoor(entry.key)
                iterator.remove()
            }
        }
    }

    private fun closeDoor(pos: BlockPos) {
        val state = level.getBlockState(pos)
        val door = state.block as? DoorBlock ?: return
        if (DoorBlock.isWoodenDoor(state)) door.setOpen(mob, level, state, pos, false)
    }

    companion object {
        private const val MAX_LOOKAHEAD_NODES = 4
        private const val DEBUG_SYNC_INTERVAL = 10
        private const val DOOR_NEAR_DISTANCE_SQ = 2.25
    }
}
