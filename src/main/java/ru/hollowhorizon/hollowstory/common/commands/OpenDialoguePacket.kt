package ru.hollowhorizon.hollowstory.common.commands

import net.minecraft.client.Minecraft
import ru.hollowhorizon.hc.common.network.HollowPacketV2
import ru.hollowhorizon.hc.common.network.Packet
import ru.hollowhorizon.hollowstory.client.screen.DialogueScreen
import ru.hollowhorizon.hollowstory.common.files.HollowStoryDirHelper.fromReadablePath

@HollowPacketV2
class OpenDialoguePacket : Packet<String>({ player, file ->
    Minecraft.getInstance().setScreen(DialogueScreen(
        fromReadablePath(file)
    ) { null })
})