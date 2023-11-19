package ru.hollowhorizon.hollowengine.common.scripting.story

import dev.ftb.mods.ftbteams.data.Team
import net.minecraft.server.MinecraftServer
import ru.hollowhorizon.hc.common.scripting.kotlin.AbstractHollowScriptConfiguration
import kotlin.script.experimental.annotations.KotlinScript
import kotlin.script.experimental.api.baseClass
import kotlin.script.experimental.api.defaultImports

@KotlinScript(
    displayName = "Story Script",
    fileExtension = "se.kts",
    compilationConfiguration = StoryScriptConfiguration::class
)
abstract class StoryScript(server: MinecraftServer, team: Team) : StoryStateMachine(server, team)

class StoryScriptConfiguration : AbstractHollowScriptConfiguration({
    defaultImports(
        "ru.hollowhorizon.hollowengine.common.scripting.story.nodes.*",
        "ru.hollowhorizon.hollowengine.common.scripting.story.nodes.base.*",
        "ru.hollowhorizon.hollowengine.common.scripting.story.nodes.base.events.*",
        "ru.hollowhorizon.hollowengine.common.scripting.story.nodes.camera.*",
        "ru.hollowhorizon.hollowengine.common.scripting.story.nodes.dialogues.*",
        "ru.hollowhorizon.hollowengine.common.scripting.story.nodes.npcs.*",
        "ru.hollowhorizon.hollowengine.common.scripting.story.*",
        "ru.hollowhorizon.hollowengine.common.scripting.*",
        "ru.hollowhorizon.hollowengine.common.npcs.*",
        "ru.hollowhorizon.hollowengine.common.entities.NPCEntity",
        "ru.hollowhorizon.hollowengine.common.util.Keybind",
        "ru.hollowhorizon.hollowengine.common.util.KeyAction",
        "ru.hollowhorizon.hc.client.models.gltf.animations.AnimationType",
        "ru.hollowhorizon.hc.client.models.gltf.animations.PlayType",
        "ru.hollowhorizon.hc.client.utils.math.Interpolation",
        "net.minecraftforge.event.*",
        "net.minecraft.core.BlockPos",
        "ru.hollowhorizon.hc.client.utils.*",
        "net.minecraft.world.level.Level"
    )

    baseClass(StoryStateMachine::class)
})