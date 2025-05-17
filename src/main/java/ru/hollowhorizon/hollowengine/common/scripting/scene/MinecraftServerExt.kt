package ru.hollowhorizon.hollowengine.common.scripting.scene

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import net.minecraft.server.MinecraftServer


interface MinecraftServerExt {
    val `hollowcore$dispatcher`: CoroutineDispatcher
    val `hollowcore$coroutineScope`: CoroutineScope
}

private val MinecraftServer.ext get() = this as MinecraftServerExt
val MinecraftServer.dispatcher get() = ext.`hollowcore$dispatcher`
val MinecraftServer.coroutineScope get() = ext.`hollowcore$coroutineScope`