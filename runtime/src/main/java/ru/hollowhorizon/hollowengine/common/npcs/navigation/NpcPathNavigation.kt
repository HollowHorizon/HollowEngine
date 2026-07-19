package ru.hollowhorizon.hollowengine.common.npcs.navigation

import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation
import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.DoorBlock
import net.minecraft.world.level.pathfinder.PathFinder
import net.minecraft.world.phys.Vec3
import kotlin.math.sqrt
import net.minecraft.world.level.pathfinder.PathType as BlockPathTypes

class NpcPathNavigation(level: Level, mob: Mob) : GroundPathNavigation(mob, level) {
    private val openedDoors = mutableMapOf<BlockPos, Boolean>()

    override fun createPathFinder(maxVisitedNodes: Int): PathFinder {
        val evaluator = NpcNodeEvaluator()
        nodeEvaluator = evaluator
        return NpcPathFinder(evaluator)
    }

    override fun tick() {
        super.tick()
        closePassedDoors()
        if (path?.isDone == true) return

        val node = path?.nextNode ?: return

        if (node.type == BlockPathTypes.WALKABLE_DOOR) {
            val state = level.getBlockState(node.asBlockPos())
            if (DoorBlock.isWoodenDoor(state)) {
                val door = state.block as DoorBlock
                door.setOpen(mob, level, state, node.asBlockPos(), true)
                openedDoors.putIfAbsent(node.asBlockPos(), false)
            }
        }

        val pos = node as? JumpNode ?: return

        val dx: Double = pos.x + 0.5 - mob.x
        val dy: Double = pos.y - mob.y
        val dz: Double = pos.z + 0.5 - mob.z
        val distance = sqrt(dx * dx + dz * dz)
        val gravity = 0.08
        val horizontalSpeed = mob.attributes.getValue(Attributes.MOVEMENT_SPEED)
        if (distance < MIN_JUMP_DISTANCE || horizontalSpeed <= MIN_JUMP_SPEED) return
        val velocityX = dx / distance * horizontalSpeed
        val velocityZ = dz / distance * horizontalSpeed
        val time = distance / horizontalSpeed
        val velocityY = dy / time + 0.5 * gravity * time

        // Устанавливаем deltaMovement
        mob.deltaMovement = Vec3(velocityX, velocityY, velocityZ)
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
        private const val MIN_JUMP_DISTANCE = 1.0e-4
        private const val MIN_JUMP_SPEED = 1.0e-4
        private const val DOOR_NEAR_DISTANCE_SQ = 2.25
    }
}
