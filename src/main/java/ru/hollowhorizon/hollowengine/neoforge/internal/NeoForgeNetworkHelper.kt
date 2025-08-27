package ru.hollowhorizon.hollowengine.neoforge.internal

//? if neoforge {

/*import net.minecraft.nbt.CompoundTag
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.neoforged.neoforge.network.PacketDistributor
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent
import net.neoforged.neoforge.network.registration.PayloadRegistrar
import ru.hollowhorizon.hollowengine.HollowCore
import ru.hollowhorizon.hollowengine.HollowCore.MODID
import ru.hollowhorizon.hollowengine.common.network.*
import ru.hollowhorizon.hollowengine.common.utils.nbt.NBTFormat
import ru.hollowhorizon.hollowengine.common.utils.nbt.deserializeNoInline
import ru.hollowhorizon.hollowengine.common.utils.nbt.serializeNoInline
import ru.hollowhorizon.hollowengine.common.utils.rl

object NeoForgeNetworkHelper {
    fun register(event: RegisterPayloadHandlersEvent) {
        val registrar: PayloadRegistrar = event.registrar("1")
        registerPacket = { type ->
            registerPacket(registrar, type as Class<HollowPacket>)
        }
        sendPacketToClient = { player, hollowPacketV3 ->
            PacketDistributor.sendToPlayer(player, hollowPacketV3)
        }
        sendPacketToServer = { hollowPacketV3 ->
            PacketDistributor.sendToServer(hollowPacketV3)
        }
        registerPackets.invoke()
    }
}

fun <T : HollowPacket> registerPacket(registerer: PayloadRegistrar, type: Class<T>) {
    val annotation = type.getAnnotation(HollowPacketHandler::class.java)
    val location = CustomPacketPayload.Type<T>("hollowengine:${type.name.lowercase().filter { ResourceLocation.validPathChar(it) }}".rl)

    val codec: StreamCodec<FriendlyByteBuf, T> = CustomPacketPayload.codec(
        { packet, buffer ->
            val tag = NBTFormat.serializeNoInline(packet, type)
            if (tag is CompoundTag) buffer.writeNbt(tag)
            else buffer.writeNbt(CompoundTag().apply { put("data", tag) })
        },
        { buffer ->
            try {
                val tag = buffer.readNbt() ?: throw IllegalStateException("NBT is null")
                if (tag.contains("data")) NBTFormat.deserializeNoInline(tag.get("%%data")!!, type)
                else NBTFormat.deserializeNoInline(tag, type)
            } catch (e: Exception) {
                // Без этого эта ошибка затеряется фиг пойми где, а так будет хоть какая-то информация
                HollowCore.LOGGER.error("Can't decode ${type.name} packet!", e)
                throw e
            }
        }
    )

    when (annotation.toTarget) {
        HollowPacketHandler.Direction.TO_CLIENT -> {
            registerer.playToClient(location, codec) { payload, context ->
                payload.handle(context.player())
            }
        }

        HollowPacketHandler.Direction.TO_SERVER -> {
            registerer.playToServer(location, codec) { payload, context ->
                payload.handle(context.player())
            }
        }

        HollowPacketHandler.Direction.ANY -> {
            registerer.playBidirectional(location, codec) { payload, context ->
                payload.handle(context.player())
            }
        }
    }
}

*///?}