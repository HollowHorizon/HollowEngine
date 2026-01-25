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

private val MinecraftServer.ext get() = this as ServerDispatcher
val MinecraftServer.dispatcher get() = ext.`hollowcore$dispatcher`
val MinecraftServer.coroutineScope get() = ext.`hollowcore$coroutineScope`
val Level.coroutineScope: CoroutineScope
    get() = server?.coroutineScope ?: error("Server not found!")

private val Minecraft.ext get() = this as ClientDispatcher
val Minecraft.dispatcher get() = ext.`hollowcore$dispatcher`
val Minecraft.coroutineScope get() = ext.`hollowcore$coroutineScope`