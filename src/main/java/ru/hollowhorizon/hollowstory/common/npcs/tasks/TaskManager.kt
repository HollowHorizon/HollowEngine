package ru.hollowhorizon.hollowstory.common.npcs.tasks

import ru.hollowhorizon.hollowstory.common.entities.NPCEntity

class TaskManager(val npc: NPCEntity) {
    fun makeTask(task: TaskBuilder.() -> Unit) {
        task.invoke(TaskBuilder(this))
    }
}