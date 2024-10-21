package ru.hollowhorizon.hollowengine.common.scripting.story

import ru.hollowhorizon.hollowengine.common.scripting.core.example.AbstractHollowScriptConfiguration
import ru.hollowhorizon.hollowengine.scripting.nodes.Node
import kotlin.script.experimental.annotations.KotlinScript

@KotlinScript(
    displayName = "Story Event",
    fileExtension = "story.kts",
    compilationConfiguration = StoryEventConfiguration::class
)
class StoryEvent {
    lateinit var script: Node // Плагин для компилятора сам инициализирует эту переменную на основе данных из скрипта
}

class StoryEventConfiguration : AbstractHollowScriptConfiguration({})


