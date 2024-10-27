package ru.hollowhorizon.hollowengine.common.scripting.story

import ru.hollowhorizon.hollowengine.common.scripting.core.configuration.HollowScriptConfiguration
import ru.hollowhorizon.hollowengine.compiler.suspendable.SuspendContext
import kotlin.script.experimental.annotations.KotlinScript
import kotlin.script.experimental.api.defaultImports

@KotlinScript(
    displayName = "Story Event",
    fileExtension = "story.kts",
    compilationConfiguration = StoryConfiguration::class
)
abstract class StoryEvent {
    abstract fun tick(context: SuspendContext): Any?
}

class StoryConfiguration: HollowScriptConfiguration({
    defaultImports(
        "ru.hollowhorizon.hollowengine.common.scripting.story.functions.npcs.*"
    )
})

