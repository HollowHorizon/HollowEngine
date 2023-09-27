package ru.hollowhorizon.hollowengine.common.network

import net.minecraft.network.chat.Component
import net.minecraft.network.chat.TextComponent
import net.minecraftforge.network.NetworkDirection
import ru.hollowhorizon.hc.client.utils.mc
import ru.hollowhorizon.hc.common.network.HollowPacketV2
import ru.hollowhorizon.hc.common.network.Packet

@HollowPacketV2(toTarget = NetworkDirection.PLAY_TO_CLIENT)

class CopyTextPacket : Packet<String>({ player, value ->
    mc.player!!.sendMessage(TextComponent("§6[§bHollow Engine§6] §bItem: $value"), mc.player!!.uuid)
    mc.keyboardHandler.clipboard = value
})