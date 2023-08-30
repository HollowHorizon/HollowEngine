package ru.hollowhorizon.hollowengine.common.network

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraftforge.network.NetworkDirection
import ru.hollowhorizon.hc.common.network.HollowPacketV2
import ru.hollowhorizon.hc.common.network.Packet

@HollowPacketV2(toTarget = NetworkDirection.PLAY_TO_CLIENT)
class OpenChatPacket : Packet<String>({ player, value ->
    Minecraft.getInstance().setScreen(ChatScreen(""))
})