package ru.hollowhorizon.hollowengine.common.scripting.dialogues.executors

import kotlinx.serialization.Serializable
import net.minecraft.world.entity.player.Player
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.api.distmarker.OnlyIn
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.eventbus.api.Event
import ru.hollowhorizon.hc.client.utils.open
import ru.hollowhorizon.hc.common.network.HollowPacketV2
import ru.hollowhorizon.hc.common.network.Packet
import ru.hollowhorizon.hc.common.network.send
import ru.hollowhorizon.hollowengine.client.screen.DialogueScreen

@OnlyIn(Dist.CLIENT)
val DIALOGUE_SCREEN = DialogueScreen()

@HollowPacketV2
class OpenScreenPacket : Packet<Int>({ player, value ->
    DIALOGUE_SCREEN.open()
})

@HollowPacketV2
class CloseScreenPacket : Packet<Int>({ player, value ->
    DIALOGUE_SCREEN.onClose()
})

@HollowPacketV2
class WaitActionPacketS2C : Packet<Int>({ player, value ->
    Thread {
        DIALOGUE_SCREEN.waitClick()
        WaitActionPacketC2S().send(0)
    }.start()
})

@HollowPacketV2
class WaitActionPacketC2S : Packet<Int>({ player, value ->
    MinecraftForge.EVENT_BUS.post(WaitActionEvent(player))
})

@HollowPacketV2
class UpdateScenePacket :
    Packet<ru.hollowhorizon.hollowengine.common.scripting.dialogues.DialogueScene>({ player, scene ->
        DIALOGUE_SCREEN.update(scene)
    })

@Serializable
class ChoicesContainer(val choices: Collection<String>)

@HollowPacketV2
class ApplyChoicePacketS2C : Packet<ChoicesContainer>({ player, choices ->
    Thread {
        ApplyChoicePacketC2S().send(DIALOGUE_SCREEN.applyChoices(choices.choices))
    }.start()
})

@HollowPacketV2
class ApplyChoicePacketC2S : Packet<Int>({ player, value ->
    MinecraftForge.EVENT_BUS.post(ApplyChoiceEvent(player, value))
})

class WaitActionEvent(val player: Player) : Event()
class ApplyChoiceEvent(val player: Player, val choice: Int) : Event()
