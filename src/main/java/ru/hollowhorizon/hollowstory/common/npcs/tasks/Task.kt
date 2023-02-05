package ru.hollowhorizon.hollowstory.common.npcs.tasks

import net.minecraft.entity.ai.goal.Goal
import ru.hollowhorizon.hollowstory.common.entities.NPCEntity

open class Task(val npc: NPCEntity) : Goal() {
    val waiter = Object()
    val navigation = npc.navigation

    fun pause() {
        synchronized(waiter) {
            waiter.wait()
        }
    }

    fun resume() {
        synchronized(waiter) {
            waiter.notify()
        }
    }

    override fun canUse(): Boolean {
        return true
    }
}