package ru.hollowhorizon.hollowengine.common.npcs.navigation

import net.minecraft.world.level.pathfinder.WalkNodeEvaluator

class NpcNodeEvaluator : WalkNodeEvaluator() {
    init {
        setCanFloat(true)
        setCanOpenDoors(true)
        setCanPassDoors(true)
    }

}