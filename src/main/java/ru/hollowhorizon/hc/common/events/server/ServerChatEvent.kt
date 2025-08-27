package ru.hollowhorizon.hc.common.events.server

import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import ru.hollowhorizon.hc.common.events.Cancelable
import ru.hollowhorizon.hc.common.events.Event

class ServerChatEvent(
    val player: ServerPlayer, var message: Component,
) : Event, Cancelable {
    override var isCanceled = false
    val username get() = player.gameProfile.name
}