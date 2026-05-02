package ru.hollowhorizon.hollowengine.client.gui.timeline.cutscene

import kotlinx.serialization.Serializable
import net.minecraft.world.entity.player.Player
import ru.hollowhorizon.hollowengine.common.network.HollowPacket
import ru.hollowhorizon.hollowengine.common.network.HollowPacketHandler

@HollowPacketHandler(HollowPacketHandler.Direction.TO_CLIENT)
@Serializable
class PlayCutscenePacket(private val path: String) : HollowPacket {
    override fun handle(player: Player) {
        val data = CutsceneStorage.load(path)
        CutsceneCameraSystem.play(data)
    }
}
