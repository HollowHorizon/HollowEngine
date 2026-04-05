package ru.hollowhorizon.hollowengine.common.coroutines

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import net.minecraft.client.Minecraft
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.Level

interface ServerDispatcher {
    val `hollowcore$dispatcher`: CoroutineDispatcher
    val `hollowcore$coroutineScope`: CoroutineScope
}

interface ClientDispatcher {
    val `hollowcore$dispatcher`: CoroutineDispatcher
    val `hollowcore$coroutineScope`: CoroutineScope
}

val MinecraftServer.dispatcher get() = RuntimeDispatcherState.serverDispatcher(this)
val MinecraftServer.coroutineScope get() = RuntimeDispatcherState.serverScope(this)
val Level.coroutineScope: CoroutineScope
    get() = server?.coroutineScope ?: error("Server not found!")

val Minecraft.dispatcher get() = RuntimeDispatcherState.clientDispatcher(this)
val Minecraft.coroutineScope get() = RuntimeDispatcherState.clientScope(this)
val Minecraft.coroutineScopeOrNull get() = RuntimeDispatcherState.clientScopeOrNull(this)
