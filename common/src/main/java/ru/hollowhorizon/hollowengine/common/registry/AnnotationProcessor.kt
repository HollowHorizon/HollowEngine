package ru.hollowhorizon.hollowengine.common.registry

import ru.hollowhorizon.hc.common.events.AnnotationProcessorEvent
import ru.hollowhorizon.hc.common.events.SubscribeEvent
import ru.hollowhorizon.hollowengine.common.npcs.quests.tasks.QuestTask

annotation class QTask

val QUEST_TASKS = ArrayList<() -> QuestTask>()

@SubscribeEvent
fun onRegisterAnnotations(event: AnnotationProcessorEvent) {
    event.registerClassHandler<QTask> { clazz, _: QTask ->
        QUEST_TASKS.add { clazz.getDeclaredConstructor().newInstance() as QuestTask }
    }
}