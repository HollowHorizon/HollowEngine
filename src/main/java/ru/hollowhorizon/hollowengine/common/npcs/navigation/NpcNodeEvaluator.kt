package ru.hollowhorizon.hollowengine.common.npcs.navigation

//? if > 1.20.1 {
//?} else {
import net.minecraft.world.level.pathfinder.BlockPathTypes
import net.minecraft.world.level.pathfinder.Node
//?}
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator

class NpcNodeEvaluator : WalkNodeEvaluator() {
    init {
        setCanFloat(true)
        setCanOpenDoors(true)
        setCanPassDoors(true)
    }

    // TODO: Port on 1.21.1

    //? if > 1.20.1 {
    //?} else {
    
    override fun isDiagonalValid(root: Node, xNode: Node?, zNode: Node?, diagonal: Node?): Boolean {
        if (diagonal == null || diagonal.closed) return false

        // Разрешаем двери, если они проходимы
        val isBlockedByDoor = diagonal.type == BlockPathTypes.WALKABLE_DOOR
                || xNode?.type == BlockPathTypes.WALKABLE_DOOR
                || zNode?.type == BlockPathTypes.WALKABLE_DOOR
        if (isBlockedByDoor) return false

        // Проверка, разрешено ли движение по диагонали с подъемом не более чем на 1 блок
        fun isNodeValid(node: Node?): Boolean {
            if (node == null || node.closed) return false
            if (node.y > root.y + 1) return false // выше чем на 1 блок — нельзя
            return node.costMalus >= 0.0f
        }

        val isZNodeValid = isNodeValid(zNode)
        val isXNodeValid = isNodeValid(xNode)

        // Узкое пространство между заборами
        val isNarrowFence = zNode != null && xNode != null &&
                zNode.type == BlockPathTypes.FENCE && xNode.type == BlockPathTypes.FENCE &&
                mob.bbWidth.toDouble() < 0.5

        return diagonal.costMalus >= 0.0f && (isZNodeValid || isXNodeValid || isNarrowFence)
    }
     //?}
}