package ru.hollowhorizon.hollowstory.common.npcs.tasks.impl

import net.minecraft.entity.Entity
import net.minecraft.entity.ai.controller.LookController
import ru.hollowhorizon.hollowstory.common.entities.NPCEntity
import ru.hollowhorizon.hollowstory.common.npcs.tasks.Task

class NPCGoToEntityTask(npc: NPCEntity, val target: Entity, val stopDist: Float = 1.0F) : Task(npc) {
    private var timeToRecalcPath = 0

    override fun canContinueToUse(): Boolean {
        return npc.distanceTo(target) > stopDist
    }

    override fun start() {
        super.start()
        npc.navigation.moveTo(target, 1.0)
    }

    override fun tick() {
        super.tick()
        if (!this.npc.isLeashed) {
            this.npc.lookControl.setLookAt(this.target, 10.0f, this.npc.maxHeadXRot.toFloat())
            if (--timeToRecalcPath <= 0) {
                timeToRecalcPath = 10
                val d0: Double = this.npc.x - this.target.x
                val d1: Double = this.npc.y - this.target.y
                val d2: Double = this.npc.z - this.target.z
                val d3 = d0 * d0 + d1 * d1 + d2 * d2
                if (d3 > this.stopDist * this.stopDist) {
                    navigation.moveTo(this.target, 1.0)
                } else {
                    navigation.stop()
                    val lookcontroller: LookController = this.npc.lookControl
                    if (d3 <= stopDist || lookcontroller.wantedX == this.npc.x && lookcontroller.wantedY == this.npc.y && lookcontroller.wantedZ == this.npc.z) {
                        val d4: Double = this.target.x - this.npc.x
                        val d5: Double = this.target.z - this.npc.z
                        navigation.moveTo(npc.x - d4, npc.y, npc.z - d5, 1.0)
                    }
                }
            }
        }
    }
}