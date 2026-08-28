package ru.hollowhorizon.hollowengine.network

import io.netty.buffer.Unpooled
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.world.entity.player.Player
import ru.hollowhorizon.hollowengine.HollowEngine
import ru.hollowhorizon.hollowengine.common.network.HollowAddonPacket
import ru.hollowhorizon.hollowengine.common.network.HollowPacketHandler
import ru.hollowhorizon.hollowengine.common.utils.bytebuf.ByteBufFormat
import ru.hollowhorizon.hollowengine.common.utils.bytebuf.deserializeNoInline
import ru.hollowhorizon.hollowengine.common.utils.bytebuf.serializeNoInline
import ru.hollowhorizon.hollowengine.common.utils.rl
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

internal object HollowAddonPacketRegistry {
    private val lock = ReentrantReadWriteLock()
    private val packetsByKey = HashMap<String, RegisteredAddonPacket>()
    private val packetsByType = HashMap<Class<out HollowAddonPacket>, RegisteredAddonPacket>()

    fun register(addonId: String, type: Class<out HollowAddonPacket>) {
        val annotation = requireNotNull(type.getAnnotation(HollowPacketHandler::class.java)) {
            "Addon packet must be annotated with @HollowPacketHandler: ${type.name}"
        }
        register(addonId, type, annotation.toTarget)
    }

    fun register(
        addonId: String,
        type: Class<out HollowAddonPacket>,
        direction: HollowPacketHandler.Direction,
    ) {
        val key = HollowAddonPacket.nameFor(addonId, type)
        val registration = RegisteredAddonPacket(addonId, key, type, direction)

        lock.write {
            require(key !in packetsByKey) { "Duplicate addon packet key '$key'" }
            require(type !in packetsByType) { "Addon packet is already registered: ${type.name}" }
            packetsByKey[key] = registration
            packetsByType[type] = registration
        }
    }

    fun unregister(addonId: String, type: Class<out HollowAddonPacket>) {
        lock.write {
            val registration = packetsByType[type]?.takeIf { it.addonId == addonId } ?: return@write
            packetsByType.remove(type)
            packetsByKey.remove(registration.key)
        }
    }

    fun unregister(addonId: String) {
        lock.write {
            val removed = packetsByKey.values.filter { registration -> registration.addonId == addonId }
            removed.forEach { registration ->
                packetsByKey.remove(registration.key)
                packetsByType.remove(registration.type)
            }
        }
    }

    fun encodeForServer(packet: HollowAddonPacket): AddonPacketPayload =
        encode(packet, HollowPacketHandler.Direction.TO_SERVER)

    fun encodeForClient(packet: HollowAddonPacket): AddonPacketPayload =
        encode(packet, HollowPacketHandler.Direction.TO_CLIENT)

    fun dispatchClient(payload: AddonPacketPayload, player: Player) {
        dispatch(payload, player, HollowPacketHandler.Direction.TO_CLIENT)
    }

    fun dispatchServer(payload: AddonPacketPayload, player: Player) {
        dispatch(payload, player, HollowPacketHandler.Direction.TO_SERVER)
    }

    private fun dispatch(
        payload: AddonPacketPayload,
        player: Player,
        target: HollowPacketHandler.Direction,
    ) = lock.read {
        val registration = packetsByKey[payload.key]
        if (registration == null) {
            HollowEngine.LOGGER.debug("Ignoring unavailable addon packet '{}'", payload.key)
            return@read
        }
        if (registration.direction != HollowPacketHandler.Direction.ANY && registration.direction != target) {
            HollowEngine.LOGGER.warn("Ignoring addon packet '{}' received in an unsupported direction", payload.key)
            return@read
        }
        deserialize(payload, registration).handle(player)
    }

    internal fun decode(payload: AddonPacketPayload): HollowAddonPacket? = lock.read {
        val registration = packetsByKey[payload.key] ?: return@read null
        deserialize(payload, registration)
    }

    private fun deserialize(
        payload: AddonPacketPayload,
        registration: RegisteredAddonPacket,
    ): HollowAddonPacket {
        val buffer = FriendlyByteBuf(Unpooled.wrappedBuffer(payload.data))
        return try {
            @Suppress("UNCHECKED_CAST")
            val type = registration.type as Class<HollowAddonPacket>
            ByteBufFormat.deserializeNoInline<HollowAddonPacket>(buffer, type)
        } finally {
            buffer.release()
        }
    }

    private fun encode(
        packet: HollowAddonPacket,
        target: HollowPacketHandler.Direction,
    ): AddonPacketPayload = lock.read {
        val registration = requireNotNull(packetsByType[packet.javaClass]) {
            "Addon packet is not registered: ${packet.javaClass.name}"
        }
        require(registration.direction == HollowPacketHandler.Direction.ANY || registration.direction == target) {
            "Addon packet '${registration.key}' cannot be sent $target"
        }
        val buffer = FriendlyByteBuf(Unpooled.buffer())
        try {
            @Suppress("UNCHECKED_CAST")
            val type = registration.type as Class<HollowAddonPacket>
            ByteBufFormat.serializeNoInline(packet, type, buffer)
            val data = ByteArray(buffer.readableBytes())
            buffer.getBytes(buffer.readerIndex(), data)
            AddonPacketPayload(registration.key, data)
        } finally {
            buffer.release()
        }
    }

    private data class RegisteredAddonPacket(
        val addonId: String,
        val key: String,
        val type: Class<out HollowAddonPacket>,
        val direction: HollowPacketHandler.Direction,
    )
}

internal data class AddonPacketPayload(
    val key: String,
    val data: ByteArray,
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<AddonPacketPayload> = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<AddonPacketPayload>("hollowengine:addon_packet".rl)
        val CODEC: StreamCodec<RegistryFriendlyByteBuf, AddonPacketPayload> = CustomPacketPayload.codec(
            { payload, buffer ->
                buffer.writeUtf(payload.key)
                buffer.writeByteArray(payload.data)
            },
            { buffer -> AddonPacketPayload(buffer.readUtf(), buffer.readByteArray()) },
        )
    }
}
