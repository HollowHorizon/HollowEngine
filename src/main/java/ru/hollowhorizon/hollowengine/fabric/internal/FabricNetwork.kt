package ru.hollowhorizon.hollowengine.fabric.internal

//? if fabric && >= 1.21 {
/*import net.fabricmc.api.EnvType
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import ru.hollowhorizon.hollowengine.common.network.HollowPacket
import ru.hollowhorizon.hollowengine.common.network.HollowPacketHandler
import ru.hollowhorizon.hollowengine.common.utils.bytebuf.ByteBufFormat
import ru.hollowhorizon.hollowengine.common.utils.bytebuf.deserializeNoInline
import ru.hollowhorizon.hollowengine.common.utils.bytebuf.serializeNoInline
import ru.hollowhorizon.hollowengine.common.utils.rl

fun <T : HollowPacket> registerPacket(type: Class<T>) {
    val annotation = type.getAnnotation(HollowPacketHandler::class.java)
    val location = CustomPacketPayload.Type<T>(
        "hollowengine:${
            type.name.lowercase().filter { ResourceLocation.validPathChar(it) }
        }".rl
    )

    val codec: StreamCodec<RegistryFriendlyByteBuf, T> = CustomPacketPayload.codec(
        { packet, buffer ->
            ByteBufFormat.serializeNoInline(packet, type, buffer)
        },
        { buffer ->
            ByteBufFormat.deserializeNoInline(buffer, type)
        }
    )

    val isClient = FabricLoader.getInstance().environmentType == EnvType.CLIENT

    when (annotation.toTarget) {
        HollowPacketHandler.Direction.TO_CLIENT -> {
            PayloadTypeRegistry.playS2C()
                .register(location, codec)
            if (isClient) ClientPlayNetworking.registerGlobalReceiver(location) { payload: T, context: ClientPlayNetworking.Context ->
                payload.handle(context.player())
            }
        }

        HollowPacketHandler.Direction.TO_SERVER -> {
            PayloadTypeRegistry.playC2S()
                .register(location, codec)
            ServerPlayNetworking.registerGlobalReceiver(location) { payload: T, context: ServerPlayNetworking.Context ->
                payload.handle(context.player())
            }
        }

        HollowPacketHandler.Direction.ANY -> {
            PayloadTypeRegistry.playC2S()
                .register(location, codec)
            PayloadTypeRegistry.playS2C()
                .register(location, codec)
            ServerPlayNetworking.registerGlobalReceiver(location) { payload: T, context: ServerPlayNetworking.Context ->
                payload.handle(context.player())
            }
            if (isClient) ClientPlayNetworking.registerGlobalReceiver(location) { payload: T, context: ClientPlayNetworking.Context ->
                payload.handle(context.player())
            }
        }
    }
}

*///?} elif fabric {

/*import net.fabricmc.api.EnvType
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.network.FriendlyByteBuf
import ru.hollowhorizon.hollowengine.HollowCore
import ru.hollowhorizon.hollowengine.HollowCore.MODID
import ru.hollowhorizon.hollowengine.common.network.HollowPacket
import ru.hollowhorizon.hollowengine.common.network.HollowPacketHandler
import ru.hollowhorizon.hollowengine.common.utils.bytebuf.ByteBufFormat
import ru.hollowhorizon.hollowengine.common.utils.bytebuf.deserializeNoInline
import ru.hollowhorizon.hollowengine.common.utils.rl

fun <T : HollowPacket> registerPacket(type: Class<T>) {
    val annotation = type.getAnnotation(HollowPacketHandler::class.java)
    val location = "$MODID:${type.name.lowercase().replace("\$", ".")}".rl


    val deserializer: (FriendlyByteBuf) -> T = { buffer ->
        ByteBufFormat.deserializeNoInline(buffer, type)
    }

    val isClient = FabricLoader.getInstance().environmentType == EnvType.CLIENT

    when (annotation.toTarget) {
        HollowPacketHandler.Direction.TO_CLIENT -> {
            if (isClient) ClientPlayNetworking.registerGlobalReceiver(
                location
            ) { client, _, buf, _ ->
                val packet = deserializer(buf)
                client.execute {
                    val player = client.player

                    if (player == null) {
                        HollowCore.LOGGER.warn("No player found in minecraft... How do you receive that ${type.simpleName}?")
                        return@execute
                    }

                    packet.handle(player)
                }
            }
        }

        HollowPacketHandler.Direction.TO_SERVER -> {
            ServerPlayNetworking.registerGlobalReceiver(
                location
            ) { server, player, handler, buf, responseSender ->
                deserializer(buf).handle(player)
            }
        }

        HollowPacketHandler.Direction.ANY -> {
            if (isClient) ClientPlayNetworking.registerGlobalReceiver(
                location
            ) { client, handler, buf, responseSender ->
                val player = client.player
                if (player == null) {
                    HollowCore.LOGGER.error("No player found in minecraft... How do you receive that ${type.simpleName}?")
                    return@registerGlobalReceiver
                }
                deserializer(buf).handle(player)
            }
            ServerPlayNetworking.registerGlobalReceiver(
                location
            ) { server, player, handler, buf, responseSender ->
                deserializer(buf).handle(player)
            }
        }
    }
}
*///?}
