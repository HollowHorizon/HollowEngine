package ru.hollowhorizon.hollowstory.common.npcs.tasks

import net.minecraft.entity.Entity
import ru.hollowhorizon.hollowstory.common.npcs.tasks.impl.NPCGoToEntityTask
import ru.hollowhorizon.hollowstory.story.StoryTeam

fun TaskBuilder.go(target: Entity) {
    this.tickedTasks.add {
        this.manager.npc.navigation.moveTo(target, 1.0)
    }
}

fun TaskBuilder.go(target: StoryTeam) {
    this.manager.npc.goalSelector.addGoal(0, NPCGoToEntityTask(this.manager.npc, target.getAllOnline()[0].mcPlayer!!))
}