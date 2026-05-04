package ru.hollowhorizon.hollowengine.common.events.server

import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import ru.hollowhorizon.hollowengine.common.events.Cancellable
import ru.hollowhorizon.hollowengine.common.events.Event
import ru.hollowhorizon.hollowengine.common.events.factory.EventHandler

class ServerChatEvent(
    val player: ServerPlayer, var message: Component,
) : Event, Cancellable {
    companion object : EventHandler<ServerChatEvent>()

    override var isCanceled = false
    val username get() = player.gameProfile.name
}