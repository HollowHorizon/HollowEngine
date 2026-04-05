package ru.hollowhorizon.hollowengine.common.npcs.navigation

import net.minecraft.world.level.pathfinder.Node

class JumpNode(x: Int, y: Int, z: Int): Node(x, y, z) {
    companion object {
        fun fromNode(node: Node): JumpNode {
            val jump = JumpNode(node.x, node.y, node.z)
            jump.heapIdx = node.heapIdx
            jump.g = node.g
            jump.h = node.h
            jump.f = node.f
            jump.cameFrom = node.cameFrom
            jump.closed = node.closed
            jump.walkedDistance = node.walkedDistance
            jump.costMalus = node.costMalus
            jump.type = node.type
            return jump
        }
    }
}