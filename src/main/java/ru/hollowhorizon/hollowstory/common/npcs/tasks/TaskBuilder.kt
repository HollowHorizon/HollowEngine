package ru.hollowhorizon.hollowstory.common.npcs.tasks

class TaskBuilder(val manager: TaskManager) {
    val tickedTasks = mutableListOf<() -> Unit>()

    fun tick() {

    }
}