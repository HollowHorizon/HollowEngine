package ru.hollowhorizon.hollowstory.story

import ru.hollowhorizon.hc.common.scripting.AbstractHollowScriptConfiguration
import kotlin.script.experimental.annotations.KotlinScript
import kotlin.script.experimental.api.*

@KotlinScript(
    displayName = "Story Script",
    fileExtension = "se.kts",
    compilationConfiguration = StoryScriptConfiguration::class
)
abstract class StoryScript(team: StoryTeam, variables: StoryVariables, name: String) : StoryEvent(team, variables, name)

class StoryScriptConfiguration : AbstractHollowScriptConfiguration({
    defaultImports(
        "ru.hollowhorizon.hollowstory.story.*",
        "net.minecraftforge.event.*",
        "ru.hollowhorizon.hc.client.utils.*",
        "ru.hollowhorizon.hollowstory.common.npcs.*"
    )

    baseClass(StoryEvent::class)
})