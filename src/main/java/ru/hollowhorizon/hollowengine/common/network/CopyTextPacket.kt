package ru.hollowhorizon.hollowengine.common.network

import net.minecraft.network.chat.Component
import net.minecraftforge.network.NetworkDirection
import ru.hollowhorizon.hc.client.utils.mc
import ru.hollowhorizon.hc.common.network.HollowPacketV2
import ru.hollowhorizon.hc.common.network.Packet

@HollowPacketV2(toTarget = NetworkDirection.PLAY_TO_CLIENT)
class CopyTextPacket : Packet<String>({ player, value ->
    mc.player!!.sendSystemMessage(Component.translatable("hollowengine.commands.copy", value))
    mc.keyboardHandler.clipboard = value
})