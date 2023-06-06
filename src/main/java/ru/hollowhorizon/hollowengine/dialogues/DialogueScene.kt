package ru.hollowhorizon.hollowengine.dialogues

import kotlinx.serialization.Serializable
import net.minecraft.client.Minecraft
import net.minecraft.entity.player.PlayerEntity
import ru.hollowhorizon.hc.common.network.HollowPacketV2
import ru.hollowhorizon.hollowengine.common.npcs.ICharacter
import ru.hollowhorizon.hollowengine.dialogues.actions.IAction

@Serializable
class DialogueScene {
    var background: String? = null
    val characters = HashSet<ICharacter>()
    val actions = HashSet<IAction>()
    var autoSwitch = true
}
