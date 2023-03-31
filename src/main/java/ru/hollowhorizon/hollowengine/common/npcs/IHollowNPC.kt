package ru.hollowhorizon.hollowengine.common.npcs

import kotlinx.coroutines.runBlocking
import ru.hollowhorizon.hollowengine.common.entities.NPCEntity
import ru.hollowhorizon.hollowengine.common.npcs.tasks.HollowNPCTask

interface IHollowNPC {
    val npcEntity: NPCEntity

    fun makeTask(task: HollowNPCTask.() -> Unit) {
        val pendingTask = HollowNPCTask(this)
        npcEntity.goalSelector.addGoal(0, pendingTask)

        pendingTask.task()
    }

    fun waitInteract(icon: IconType) {
        setIcon(icon)
        synchronized(npcEntity.interactionWaiter) { (npcEntity.interactionWaiter as Object).wait() }
        setIcon(IconType.NONE)
    }

    fun asyncTask(task: HollowNPCTask.() -> Unit) {
        val pendingTask = HollowNPCTask(this)

        runBlocking { // Необходимо, чтобы игра не зависла, пока внутренние действия не будут выполнены
            pendingTask.task()
        }

        npcEntity.goalSelector.addGoal(0, pendingTask)
        pendingTask.async()
    }

    fun setIcon(icon: IconType) {
        npcEntity.icon = icon
    }
}

enum class IconType {
    NONE, DIALOGUE, WARNING, QUESTION
}
