package ru.hollowhorizon.hollowengine.client.gui.npcs

import de.fabmax.kool.scene.Scene
import kotlinx.serialization.Serializable
import net.minecraft.world.entity.player.Player
import ru.hollowhorizon.hc.common.events.Event
import ru.hollowhorizon.hc.common.events.post
import ru.hollowhorizon.hc.common.network.HollowPacketV2
import ru.hollowhorizon.hc.common.network.HollowPacketV3
import ru.hollowhorizon.hollowengine.client.gui.KoolGui
import ru.hollowhorizon.hollowengine.common.entities.NPCEntity

class NPCMenuGui(val npc: NPCEntity) : KoolGui {
    private var scale = 1f
    private val sizes = HashMap<Int, ButtonData>()

    override fun Scene.setup() {
        TODO("Not yet implemented")
    }

    class ButtonData(var size: Float)
}

@Serializable
@HollowPacketV2(HollowPacketV2.Direction.TO_SERVER)
class NpcTalkPacket(val npcId: Int) : HollowPacketV3<NpcTalkPacket> {
    override fun handle(player: Player) {
        val npc = player.level().getEntity(npcId) as? NPCEntity ?: return

        PlayerTalkToNpcEvent(npc, player).post()
    }
}

class PlayerTalkToNpcEvent(val npc: NPCEntity, val player: Player) : Event