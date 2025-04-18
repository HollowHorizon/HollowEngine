package ru.hollowhorizon.hollowengine.common.scripting.story

import net.minecraft.server.MinecraftServer
import ru.hollowhorizon.hc.common.utils.currentServer
import ru.hollowhorizon.hollowengine.common.scripting.core.configuration.HollowScriptConfiguration
import ru.hollowhorizon.hollowengine.common.scripting.story.functions.npcs.move
import ru.hollowhorizon.hollowengine.common.scripting.story.functions.npcs.npc
import ru.hollowhorizon.hollowengine.common.scripting.story.functions.npcs.pos
import ru.hollowhorizon.hollowengine.common.scripting.story.functions.npcs.say
import ru.hollowhorizon.hollowengine.scripting.Suspendable
import kotlin.script.experimental.annotations.KotlinScript
import kotlin.script.experimental.api.defaultImports

@KotlinScript(
    displayName = "Story Event",
    fileExtension = "story.kts",
    compilationConfiguration = StoryConfiguration::class
)
abstract class StoryEvent {
    val server = currentServer
    val MinecraftServer.players get() = playerList.players

    abstract fun tick(): Any?
}

class StoryConfiguration : HollowScriptConfiguration({
    defaultImports(
        "ru.hollowhorizon.hollowengine.common.scripting.story.functions.*",
        "ru.hollowhorizon.hollowengine.common.scripting.story.functions.npcs.*",
        "ru.hollowhorizon.hollowengine.common.scripting.story.functions.player.*",
        "ru.hollowhorizon.hollowengine.common.scripting.story.functions.effects.*",
        "ru.hollowhorizon.hollowengine.compiler.suspendable.async",
        "ru.hollowhorizon.hc.client.utils.*"
    )
})

@Suspendable
fun exampleScript() {
    val npc = npc(pos(0, 40, 0))

    npc say "Я живой!"
    val player = currentServer.playerList.players.random()
    npc move player

    npc say "Жесть, оно работает?!"
}