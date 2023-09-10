package ru.hollowhorizon.hollowengine.common.scripting.story

import ru.hollowhorizon.hc.common.scripting.kotlin.AbstractHollowScriptConfiguration
import kotlin.script.experimental.annotations.KotlinScript
import kotlin.script.experimental.api.baseClass
import kotlin.script.experimental.api.defaultImports

@KotlinScript(
    displayName = "Story Script",
    fileExtension = "se.kts",
    compilationConfiguration = StoryScriptConfiguration::class
)
abstract class StoryScript(team: StoryTeam, name: String) : StoryEvent(team, name)

class StoryScriptConfiguration : AbstractHollowScriptConfiguration({
    defaultImports(
        "ru.hollowhorizon.hollowengine.common.scripting.story.*",
        "ru.hollowhorizon.hollowengine.common.scripting.story.extensions.*",
        "ru.hollowhorizon.hollowengine.common.scripting.dialogues.*",
        "ru.hollowhorizon.hollowengine.common.npcs.*",
        "ru.hollowhorizon.hollowengine.common.entities.NPCEntity",
        "net.minecraftforge.event.*",
        "net.minecraft.util.math.BlockPos",
        "ru.hollowhorizon.hc.client.utils.*"
    )

    baseClass(StoryEvent::class)
})