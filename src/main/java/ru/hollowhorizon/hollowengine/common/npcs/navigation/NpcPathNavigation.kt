package ru.hollowhorizon.hollowengine.common.npcs.navigation

import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.DoorBlock
//? if > 1.20.1 {
/*import net.minecraft.world.level.pathfinder.PathType as BlockPathTypes
*///?} else {
import net.minecraft.world.level.pathfinder.BlockPathTypes
//?}
import net.minecraft.world.level.pathfinder.PathFinder
import net.minecraft.world.phys.Vec3
import kotlin.math.sqrt

class NpcPathNavigation(level: Level, mob: Mob) : GroundPathNavigation(mob, level) {
    override fun createPathFinder(maxVisitedNodes: Int): PathFinder {
        val evaluator = NpcNodeEvaluator()
        nodeEvaluator = evaluator
        return NpcPathFinder(evaluator)
    }

    override fun tick() {
        super.tick()
        if (path?.isDone == true) return

        val node = path?.nextNode ?: return

        if(node.type == BlockPathTypes.WALKABLE_DOOR) {
            val state = level.getBlockState(node.asBlockPos())
            if(DoorBlock.isWoodenDoor(state)) {
                val door = state.block as DoorBlock
                door.setOpen(mob, level, state, node.asBlockPos(), true)
            }
        }

        val pos = node as? JumpNode ?: return

        val dx: Double = pos.x + 0.5 - mob.x
        val dy: Double = pos.y - mob.y
        val dz: Double = pos.z + 0.5 - mob.z
        val distance = sqrt(dx * dx + dz * dz)
        val gravity = 0.08
        val horizontalSpeed = mob.attributes.getValue(Attributes.MOVEMENT_SPEED)
        val velocityX = (dx / distance) * horizontalSpeed
        val velocityZ = (dz / distance) * horizontalSpeed
        val time = distance / horizontalSpeed
        val velocityY = (dy / time) + 0.5 * gravity * time

        // Устанавливаем deltaMovement
        mob.deltaMovement = Vec3(velocityX, velocityY, velocityZ)
    }
}