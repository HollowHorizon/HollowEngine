package ru.hollowhorizon.hollowengine.common.commands

import net.minecraft.client.Minecraft
import ru.hollowhorizon.hc.common.network.HollowPacketV2
import ru.hollowhorizon.hc.common.network.Packet
import ru.hollowhorizon.hollowengine.client.screen.DialogueScreen
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager.fromReadablePath

@HollowPacketV2
class OpenDialoguePacket : Packet<String>({ player, file ->
    Minecraft.getInstance().setScreen(DialogueScreen(file.fromReadablePath()) {})
})