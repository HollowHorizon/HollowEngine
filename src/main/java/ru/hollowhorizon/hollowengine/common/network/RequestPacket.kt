package ru.hollowhorizon.hollowengine.common.network

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player
import ru.hollowhorizon.hollowengine.common.events.Event
import ru.hollowhorizon.hollowengine.common.events.awaitEvent
import ru.hollowhorizon.hollowengine.common.events.post

abstract class RequestPacket : HollowPacket, Event {
    override fun handle(player: Player) {
        if (player.level().isClientSide) handleClient(player)
        else handleServer(player)
    }

    private fun handleClient(player: Player) {
        this.post()
    }

    private fun handleServer(player: Player) {
        retrieveValue(player as ServerPlayer)
        this.send(player)
    }

    protected abstract fun retrieveValue(player: ServerPlayer)
}

suspend inline fun <reified T : RequestPacket> T.request(): T {
    send()
    return awaitEvent<T>()
}