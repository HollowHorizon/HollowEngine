package ru.hollowhorizon.hollowengine.common.scripting.util

import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import java.util.*

val MinecraftServer.players: MutableList<ServerPlayer> get() = playerList.players

operator fun List<ServerPlayer>.get(name: String): ServerPlayer = single { it.name.string == name }
operator fun List<ServerPlayer>.get(uuid: UUID): ServerPlayer = single { it.uuid == uuid }
