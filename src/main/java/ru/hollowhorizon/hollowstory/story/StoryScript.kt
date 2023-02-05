package ru.hollowhorizon.hollowstory.story

import net.minecraftforge.fml.ModList
import net.minecraftforge.fml.loading.FMLLoader
import ru.hollowhorizon.hc.common.scripting.AbstractHollowScriptConfiguration
import ru.hollowhorizon.hollowstory.dialogues.HollowDialogueConfiguration
import java.io.File
import kotlin.script.experimental.annotations.KotlinScript
import kotlin.script.experimental.api.*
import kotlin.script.experimental.jvm.dependenciesFromClassContext
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.jvm.updateClasspath

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
        "ru.hollowhorizon.hc.client.utils.toRL",
        "ru.hollowhorizon.hollowstory.common.npcs.*"
    )

    baseClass(StoryEvent::class)
})