package ru.hollowhorizon.hollowengine.common.registry

import ru.hollowhorizon.hc.common.events.AnnotationProcessorEvent
import ru.hollowhorizon.hc.common.events.SubscribeEvent
import ru.hollowhorizon.hollowengine.common.npcs.quests.tasks.QuestTask

// TODO: Используй ResourceLocation и Component.translatable
annotation class QTask(val name: String)

val QUEST_TASKS = HashMap<String, () -> QuestTask>()

@SubscribeEvent
fun onRegisterAnnotations(event: AnnotationProcessorEvent) {
    event.registerClassHandler<QTask> { clazz, annotation: QTask ->
        QUEST_TASKS[annotation.name] = { clazz.getDeclaredConstructor().newInstance() as QuestTask }
    }
}