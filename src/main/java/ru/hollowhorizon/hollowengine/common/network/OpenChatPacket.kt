package ru.hollowhorizon.hollowengine.common.network

import kotlinx.serialization.Serializable
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.world.entity.player.Player
import ru.hollowhorizon.hc.common.network.HollowPacketV2
import ru.hollowhorizon.hc.common.network.HollowPacketV3

@HollowPacketV2(HollowPacketV2.Direction.TO_CLIENT)
@Serializable
class OpenChatPacket(val text: String = "") : HollowPacketV3<OpenChatPacket> {
    override fun handle(player: Player, data: OpenChatPacket) {
        Minecraft.getInstance().setScreen(ChatScreen(data.text))
    }

}