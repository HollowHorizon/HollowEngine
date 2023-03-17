package ru.hollowhorizon.hollowstory.common.npcs.tasks

import net.minecraft.entity.ai.goal.Goal
import ru.hollowhorizon.hollowstory.common.npcs.IHollowNPC
import ru.hollowhorizon.hollowstory.common.npcs.tasks.look.NPCLook
import ru.hollowhorizon.hollowstory.common.npcs.tasks.movement.NPCMovement

class HollowNPCTask(val npc: IHollowNPC) : Goal() {
    val movement = NPCMovement(this)
    val look = NPCLook(this)
    private val waiter = Object()
    var isActive = false

    fun cancel() {
        notifyUpdate()
    }

    override fun canUse() = isActive

    override fun tick() {
        tickMovement()
        tickLook()

        if (!movement.isActive && !look.isActive) {
            npc.npcEntity.navigation.stop()
            notifyUpdate()
        }
    }

    private fun tickLook() {
        if (look.keyframes.isNotEmpty()) {
            look.keyframes.first().tick()
            look.keyframes.removeIf { it.isFinished() }
        } else {
            npc.npcEntity.xRot = npc.npcEntity.lookAngle.x.toFloat()
            npc.npcEntity.xRotO = npc.npcEntity.lookAngle.x.toFloat()

            look.isActive = false

            synchronized(look.waiter) {
                look.waiter.notify()
            }
        }
    }

    private fun tickMovement() {
        if (movement.keyframes.isNotEmpty()) {
            val first = movement.keyframes.first()
            first.tick()
            if (first.isFinished()) {
                movement.keyframes.remove(first)
                npc.npcEntity.navigation.stop()
            }
        } else {
            movement.isActive = false

            synchronized(movement.waiter) {
                movement.waiter.notify()
            }
        }
    }

    fun async() {
        isActive = true
    }

    fun await() {
        isActive = true
        synchronized(waiter) {
            waiter.wait()
        }
    }

    private fun notifyUpdate() {
        npc.npcEntity.goalSelector.removeGoal(this)
        isActive = false
        synchronized(waiter) {
            waiter.notify()
        }
    }
}