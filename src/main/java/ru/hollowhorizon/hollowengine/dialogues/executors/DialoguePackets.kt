package ru.hollowhorizon.hollowengine.dialogues.executors

import kotlinx.serialization.Serializable
import net.minecraft.entity.player.PlayerEntity
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.api.distmarker.OnlyIn
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.eventbus.api.Event
import ru.hollowhorizon.hc.client.utils.open
import ru.hollowhorizon.hc.common.network.HollowPacketV2
import ru.hollowhorizon.hc.common.network.Packet
import ru.hollowhorizon.hollowengine.client.screen.DialogueScreen
import ru.hollowhorizon.hollowengine.dialogues.DialogueScene

@OnlyIn(Dist.CLIENT)
val DIALOGUE_SCREEN = DialogueScreen()

@HollowPacketV2
class OpenScreenPacket : Packet<Int>({ player, value ->
    DIALOGUE_SCREEN.open()
})

@HollowPacketV2
class CloseScreenPacket: Packet<Int>({ player, value ->
    DIALOGUE_SCREEN.onClose()
})

@HollowPacketV2
class WaitActionPacketS2C: Packet<Int>({ player, value ->
    Thread {
        DIALOGUE_SCREEN.waitClick()
        WaitActionPacketC2S().send(0)
    }.start()
})

@HollowPacketV2
class WaitActionPacketC2S: Packet<Int>({ player, value ->
    MinecraftForge.EVENT_BUS.post(WaitActionEvent(player))
})

@HollowPacketV2
class UpdateScenePacket: Packet<DialogueScene>({player, scene ->
    DIALOGUE_SCREEN.update(scene)
})

@Serializable
class ChoicesContainer(val choices: Collection<String>)

@HollowPacketV2
class ApplyChoicePacketS2C: Packet<ChoicesContainer>({ player, choices ->
    Thread {
        ApplyChoicePacketC2S().send(DIALOGUE_SCREEN.applyChoices(choices.choices))
    }.start()
})

@HollowPacketV2
class ApplyChoicePacketC2S: Packet<Int>({ player, value ->
    MinecraftForge.EVENT_BUS.post(ApplyChoiceEvent(player, value))
})

class WaitActionEvent(val player: PlayerEntity): Event()
class ApplyChoiceEvent(val player: PlayerEntity, val choice: Int): Event()
