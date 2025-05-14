package ru.hollowhorizon.hollowengine.common.scripting.story

import net.minecraft.server.MinecraftServer
import net.minecraft.util.Mth
import net.minecraft.util.RandomSource
import ru.hollowhorizon.hc.common.events.server.ServerChatEvent
import ru.hollowhorizon.hc.common.utils.ModList
import ru.hollowhorizon.hc.common.utils.currentServer
import ru.hollowhorizon.hollowengine.common.scripting.core.configuration.HollowScriptConfiguration
import ru.hollowhorizon.hollowengine.common.scripting.story.functions.await
import ru.hollowhorizon.hollowengine.common.scripting.story.functions.npcs.*
import ru.hollowhorizon.hollowengine.compiler.coroutine.suspendable.SFunction0
import ru.hollowhorizon.hollowengine.scripting.Suspendable
import kotlin.random.Random
import kotlin.script.experimental.annotations.KotlinScript
import kotlin.script.experimental.api.defaultImports

@KotlinScript(
    displayName = "Inline Script",
    fileExtension = "inline.kts",
    compilationConfiguration = InlineConfiguration::class
)
abstract class InlineScript {
    val math = Mth()
    val random = Random
}
class InlineConfiguration: HollowScriptConfiguration()

@KotlinScript(
    displayName = "Story Event",
    fileExtension = "story.kts",
    compilationConfiguration = StoryConfiguration::class
)
abstract class StoryEvent: SFunction0<Any?> {
    val server = currentServer
    val MinecraftServer.players get() = playerList.players
    val random = RandomSource.create()
}

class StoryConfiguration : HollowScriptConfiguration({
    defaultImports(
        "ru.hollowhorizon.hollowengine.common.scripting.story.functions.*",
        "ru.hollowhorizon.hollowengine.common.scripting.story.functions.npcs.*",
        "ru.hollowhorizon.hollowengine.common.scripting.story.functions.player.*",
        "ru.hollowhorizon.hollowengine.common.scripting.story.functions.effects.*",
        "ru.hollowhorizon.hollowengine.common.capability.*",
        "ru.hollowhorizon.hollowengine.compiler.coroutine.async",
        "ru.hollowhorizon.hollowengine.compiler.coroutine.AsyncController",
        "net.minecraft.core.BlockPos",
        "net.minecraft.util.RandomSource",
        "net.minecraft.util.Mth",
        "net.minecraft.world.level.levelgen.Heightmap",
        "net.minecraft.world.phys.Vec3",
        "net.minecraft.world.entity.*",
        "net.minecraft.server.level.ServerLevel",
        "ru.hollowhorizon.hc.client.utils.*"
    )
    if(ModList.isLoaded("bbs")) defaultImports("ru.hollowhorizon.hollowengine.common.scripting.story.functions.bbs.*")
})

