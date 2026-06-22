package ru.hollowhorizon.hollowengine.common.scripting.story

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

abstract class StoryScript(
    val context: StoryScriptContext,
) {
    val player: ServerPlayer get() = context.player
    val server: MinecraftServer get() = context.server

    fun story(block: suspend StoryScript.() -> Unit): Job = context.launch { block() }
}

class StoryScriptContext internal constructor(
    val server: MinecraftServer,
    val player: ServerPlayer,
    val path: String,
    private val scope: CoroutineScope,
) {
    internal fun launch(block: suspend CoroutineScope.() -> Unit): Job = scope.launch(block = block)
}
