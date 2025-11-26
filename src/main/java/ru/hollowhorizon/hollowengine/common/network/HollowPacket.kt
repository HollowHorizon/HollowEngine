package ru.hollowhorizon.hollowengine.common.network

//? if forge {
import net.minecraftforge.network.PacketDistributor
import ru.hollowhorizon.hollowengine.forge.internal.ForgeNetworkHelper
//?} elif fabric {
/*import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import io.netty.buffer.Unpooled
import net.minecraft.network.FriendlyByteBuf
*///?}
import net.minecraft.network.protocol.Packet

//? if >= 1.21 {
/*import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket
*///?}
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerChunkCache
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import ru.hollowhorizon.hollowengine.HollowCore.MODID
import ru.hollowhorizon.hollowengine.common.utils.bytebuf.ByteBufFormat
import ru.hollowhorizon.hollowengine.common.utils.bytebuf.serializeNoInline
import ru.hollowhorizon.hollowengine.common.utils.rl

interface CustomPacketPayload

interface HollowPacket : CustomPacketPayload {
    fun handle(player: Player)

    fun send() {
        sendPacketToServer(this)
    }

    fun send(vararg players: ServerPlayer) {
        players.forEach {
            sendPacketToClient(it, this)
        }
    }

    //? if >= 1.21 {
    /*override fun type(): CustomPacketPayload.Type<HollowPacket> {
        return CustomPacketPayload.Type("hollowengine:${this::class.java.name.lowercase().filter { ResourceLocation.validPathChar(it) }}".rl)
    }
    *///?}
}

fun HollowPacket.send(players: Iterable<ServerPlayer>) {
    send(*players.toList().toTypedArray())
}

val HollowPacket.packetName: ResourceLocation
    get() = "$MODID:${
        this.javaClass.name.lowercase().replace("\$", ".")
    }".rl

fun HollowPacket.sendTrackingEntity(entity: Entity) {
    val chunkCache = entity.level().chunkSource
    if (chunkCache is ServerChunkCache) {
        //? if forge {
        ForgeNetworkHelper.hollowCoreChannel.send(PacketDistributor.TRACKING_ENTITY.with { entity }, this)
        //?} else {
        /*chunkCache.broadcastAndSend(
            entity, this.asVanillaPacket(true)
        )
        *///?}
    } else {
        throw IllegalStateException("Cannot send clientbound payloads on the client")
    }
}

fun HollowPacket.sendTrackingEntityAndSelf(entity: Entity) {
    sendTrackingEntity(entity)
    if (entity is ServerPlayer) send(entity)
}

fun HollowPacket.sendAllInDimension(level: Level) {
    val server = level.server ?: return
    send(server.playerList.players)
}

//? if fabric && <= 1.21 {
/*fun HollowPacket.asVanillaPacket(toClient: Boolean): Packet<*> {
    val byteBuf = FriendlyByteBuf(Unpooled.buffer())
    ByteBufFormat.serializeNoInline(this, javaClass, byteBuf)
    return if (!toClient) ClientPlayNetworking.createC2SPacket(packetName, byteBuf)
    else ServerPlayNetworking.createS2CPacket(packetName, byteBuf)

    throw NotImplementedError("AsVanillaPacket method is not implemented for this platform")
}*///?} elif >= 1.21 {
/*fun HollowPacket.asVanillaPacket(toClient: Boolean): Packet<*> =
    if (toClient) ClientboundCustomPayloadPacket(this) else ServerboundCustomPayloadPacket(this)
*///?}

lateinit var sendPacketToServer: (HollowPacket) -> Unit
lateinit var sendPacketToClient: (ServerPlayer, HollowPacket) -> Unit
lateinit var registerPacket: (Class<*>) -> Unit
lateinit var registerPackets: () -> Unit