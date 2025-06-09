package ru.hollowhorizon.hollowengine.common.scripting.scene

import net.minecraft.nbt.CompoundTag
import ru.hollowhorizon.hc.common.utils.currentServer
import ru.hollowhorizon.hollowengine.common.fsm.StateNode
import ru.hollowhorizon.hollowengine.common.scripting.core.configuration.HollowScriptConfiguration
import kotlin.script.experimental.annotations.KotlinScript
import kotlin.script.experimental.api.defaultImports

@KotlinScript(
    displayName = "State Machine Script",
    fileExtension = "scene.kts",
    compilationConfiguration = SceneScriptConfiguration::class
)
abstract class SceneScript {
    internal val stateMachine = StateNode("Root") {}
    internal var isStarted = false
    internal var isLoaded = false

    val server = currentServer

    fun script(body: suspend StateNode.() -> Unit) {
        stateMachine.initializer = body
    }


    open fun save(tag: CompoundTag) {}
    open fun load(tag: CompoundTag) {}

    open fun canResume(): Boolean = true
}

class SceneScriptConfiguration : HollowScriptConfiguration({
    defaultImports(
        "ru.hollowhorizon.hollowengine.common.scripting.story.functions.*",
        "ru.hollowhorizon.hollowengine.common.scripting.story.functions.npcs.*",
        "ru.hollowhorizon.hollowengine.common.scripting.story.functions.player.*",
        "ru.hollowhorizon.hollowengine.common.scripting.story.functions.effects.*",
        "ru.hollowhorizon.hollowengine.common.scripting.util.*",
        "ru.hollowhorizon.hollowengine.common.capability.*",
        "ru.hollowhorizon.hollowengine.common.fsm.*",
        "net.minecraft.core.BlockPos",
        "net.minecraft.util.RandomSource",
        "net.minecraft.util.Mth",
        "net.minecraft.world.level.levelgen.Heightmap",
        "net.minecraft.world.phys.Vec3",
        "net.minecraft.world.entity.*",
        "net.minecraft.server.level.ServerLevel",
        "ru.hollowhorizon.hc.client.models.internal.controller.*",
        "ru.hollowhorizon.hc.client.utils.*",
        "ru.hollowhorizon.hc.common.utils.*"
    )
})