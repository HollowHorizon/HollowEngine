package ru.hollowhorizon.hollowengine.common.network


import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.serializer
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import ru.hollowhorizon.hollowengine.common.utils.rl
import ru.hollowhorizon.hollowengine.network.CommonNetworkManager
import kotlin.reflect.KClass

interface HollowPacket : CustomPacketPayload {
    fun handle(player: Player)

    fun send() {
        CommonNetworkManager.sendToServer(this)
    }

    fun send(vararg players: ServerPlayer) {
        players.forEach {
            CommonNetworkManager.sendToClient(it, this)
        }
    }

    fun send(players: Collection<ServerPlayer>) {
        players.forEach {
            CommonNetworkManager.sendToClient(it, this)
        }
    }

    @OptIn(InternalSerializationApi::class)
    override fun type(): CustomPacketPayload.Type<HollowPacket> {
        return CustomPacketPayload.Type(nameFor(this::class).rl)
    }

    companion object {
        @OptIn(InternalSerializationApi::class)
        fun nameFor(packet: KClass<*>): String {
            packet.serializer().descriptor.serialName.lowercase().filter { ResourceLocation.validPathChar(it) }
                .let { return if (it.contains(':')) it else "hollowengine:$it" }
        }
        fun nameFor(packet: Class<*>) = nameFor(packet.kotlin)
    }
}

fun HollowPacket.send(players: Iterable<ServerPlayer>) {
    send(*players.toList().toTypedArray())
}

fun HollowPacket.sendTrackingEntity(entity: Entity) = CommonNetworkManager.sendTrackingEntity(entity, this)

fun HollowPacket.sendTrackingEntityAndSelf(entity: Entity) {
    sendTrackingEntity(entity)
    if (entity is ServerPlayer) send(entity)
}

fun HollowPacket.sendAllInDimension(level: Level) {
    val server = level.server ?: return
    send(server.playerList.players)
}