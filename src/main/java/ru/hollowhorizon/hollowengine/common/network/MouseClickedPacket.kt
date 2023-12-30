package ru.hollowhorizon.hollowengine.common.network

import kotlinx.serialization.Serializable
import net.minecraft.world.entity.player.Player
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.event.entity.player.PlayerEvent
import net.minecraftforge.network.NetworkDirection
import ru.hollowhorizon.hc.common.network.HollowPacketV2
import ru.hollowhorizon.hc.common.network.HollowPacketV3
import ru.hollowhorizon.hc.common.network.Packet
import ru.hollowhorizon.hollowengine.client.ClientEvents

@HollowPacketV2(HollowPacketV2.Direction.TO_SERVER)
@Serializable
class MouseClickedPacket(val button: MouseButton) : HollowPacketV3<MouseClickedPacket> {
    override fun handle(player: Player, data: MouseClickedPacket) {
        MinecraftForge.EVENT_BUS.post(ServerMouseClickedEvent(player, data.button))
    }
}

class ServerMouseClickedEvent(player: Player, val button: MouseButton) : PlayerEvent(player)

@HollowPacketV2(HollowPacketV2.Direction.TO_SERVER)
@Serializable
class MouseButtonWaitPacket(val button: MouseButton) : HollowPacketV3<MouseButtonWaitPacket> {
    override fun handle(player: Player, data: MouseButtonWaitPacket) {
        ClientEvents.canceledButtons.add(data.button)
    }

}

@HollowPacketV2(HollowPacketV2.Direction.TO_CLIENT)
@Serializable
class MouseButtonWaitResetPacket : HollowPacketV3<MouseButtonWaitResetPacket> {
    override fun handle(player: Player, data: MouseButtonWaitResetPacket) {
        ClientEvents.canceledButtons.clear()
    }

}

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
