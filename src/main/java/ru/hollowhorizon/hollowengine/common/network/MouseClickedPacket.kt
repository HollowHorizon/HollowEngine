package ru.hollowhorizon.hollowengine.common.network

import kotlinx.serialization.Serializable
import net.minecraft.world.entity.player.Player
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.event.entity.player.PlayerEvent
import net.minecraftforge.network.NetworkDirection
import ru.hollowhorizon.hc.common.network.HollowPacketV2
import ru.hollowhorizon.hc.common.network.Packet
import ru.hollowhorizon.hollowengine.client.ClientEvents

@HollowPacketV2(toTarget = NetworkDirection.PLAY_TO_SERVER)
class MouseClickedPacket : Packet<Container>({ player, value ->
    MinecraftForge.EVENT_BUS.post(ServerMouseClickedEvent(player, value.data))
})

class ServerMouseClickedEvent(player: Player, val button: MouseButton) : PlayerEvent(player)

@HollowPacketV2(toTarget = NetworkDirection.PLAY_TO_CLIENT)
class MouseButtonWaitPacket : Packet<Container>({ player, value ->
    ClientEvents.canceledButtons.add(value.data)
})

@HollowPacketV2(toTarget = NetworkDirection.PLAY_TO_CLIENT)
class MouseButtonWaitResetPacket : Packet<String>({ player, value ->
    ClientEvents.canceledButtons.clear()
})

@Serializable
class Container(val data: MouseButton)

enum class MouseButton {
    LEFT, RIGHT, MIDDLE;

    companion object {
        fun from(value: Int): MouseButton {
            return entries[value]
        }
    }
}
