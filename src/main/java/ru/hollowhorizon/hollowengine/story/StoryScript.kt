package ru.hollowhorizon.hollowengine.story

import net.minecraft.nbt.CompoundNBT
import ru.hollowhorizon.hc.common.scripting.AbstractHollowScriptConfiguration
import kotlin.script.experimental.annotations.KotlinScript
import kotlin.script.experimental.api.*

@KotlinScript(
    displayName = "Story Script",
    fileExtension = "se.kts",
    compilationConfiguration = StoryScriptConfiguration::class
)
abstract class StoryScript(team: StoryTeam, name: String) : StoryEvent(team, name)

class StoryScriptConfiguration : AbstractHollowScriptConfiguration({
    defaultImports(
        "ru.hollowhorizon.hollowengine.story.*",
        "ru.hollowhorizon.hollowengine.dialogues.*",
        "net.minecraftforge.event.*",
        "ru.hollowhorizon.hc.client.utils.*",
        "ru.hollowhorizon.hollowengine.common.npcs.*",
        "ru.hollowhorizon.hollowengine.story.features.*"
    )

    baseClass(StoryEvent::class)
})