package ru.hollowhorizon.hollowengine.common.scripting.story

import net.minecraft.server.MinecraftServer
import ru.hollowhorizon.hc.client.utils.currentServer
import ru.hollowhorizon.hollowengine.common.scripting.core.configuration.HollowScriptConfiguration
import ru.hollowhorizon.hollowengine.compiler.suspendable.SuspendContext
import kotlin.script.experimental.annotations.KotlinScript
import kotlin.script.experimental.api.defaultImports

//TODO: Функция ввода из чата
//TODO: Гуишки

@KotlinScript(
    displayName = "Story Event",
    fileExtension = "story.kts",
    compilationConfiguration = StoryConfiguration::class
)
abstract class StoryEvent {
    val server = currentServer
    val MinecraftServer.players get() = playerList.players

    abstract fun tick(context: SuspendContext): Any?
}

class StoryConfiguration : HollowScriptConfiguration({
    defaultImports(
        "ru.hollowhorizon.hollowengine.common.scripting.story.functions.*",
        "ru.hollowhorizon.hollowengine.common.scripting.story.functions.npcs.*",
        "ru.hollowhorizon.hollowengine.common.scripting.story.functions.player.*",
    )
})

