package ru.hollowhorizon.hollowengine.common.network

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import ru.hollowhorizon.hollowengine.network.CommonNetworkManager

/**
 * A packet owned by a dynamically loaded addon.
 *
 * Addon packets are transported through one HollowEngine payload so they can be registered and
 * removed while the game is running without mutating the platform payload registry.
 */
interface HollowAddonPacket {
    fun handle(player: Player)

    fun send() {
        CommonNetworkManager.sendAddonToServer(this)
    }

    fun send(vararg players: ServerPlayer) {
        players.forEach { player -> CommonNetworkManager.sendAddonToClient(player, this) }
    }

    fun send(players: Collection<ServerPlayer>) {
        players.forEach { player -> CommonNetworkManager.sendAddonToClient(player, this) }
    }

    companion object {
        fun nameFor(addonId: String, packet: Class<*>): String =
            "$addonId:${HollowPacket.nameFor(packet).substringAfter(':')}"
    }
}

fun HollowAddonPacket.send(players: Iterable<ServerPlayer>) {
    send(*players.toList().toTypedArray())
}

fun HollowAddonPacket.sendTrackingEntity(entity: Entity) {
    CommonNetworkManager.sendAddonTrackingEntity(entity, this)
}

fun HollowAddonPacket.sendTrackingEntityAndSelf(entity: Entity) {
    sendTrackingEntity(entity)
    if (entity is ServerPlayer) send(entity)
}

fun HollowAddonPacket.sendAllInDimension(level: Level) {
    val server = level.server ?: return
    send(server.playerList.players)
}
