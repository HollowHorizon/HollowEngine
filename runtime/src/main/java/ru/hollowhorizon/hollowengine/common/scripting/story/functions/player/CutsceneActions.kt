package ru.hollowhorizon.hollowengine.common.scripting.story.functions.player

import kotlinx.serialization.Serializable
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player
import ru.hollowhorizon.hollowengine.client.ui.ide.timeline.cutscene.CutsceneCameraSystem
import ru.hollowhorizon.hollowengine.client.ui.ide.timeline.cutscene.CutsceneStorage
import ru.hollowhorizon.hollowengine.common.network.HollowPacket
import ru.hollowhorizon.hollowengine.common.network.HollowPacketHandler

fun Player.playCutscene(path: String, loop: Boolean = false) {
    val player = this as? ServerPlayer ?: return
    PlayCutscenePacket(path, loop).send(player)
}

fun Player.stopCutscene() {
    val player = this as? ServerPlayer ?: return
    StopCutscenePacket.send(player)
}

@HollowPacketHandler(HollowPacketHandler.Direction.TO_CLIENT)
@Serializable
class PlayCutscenePacket(
    val path: String,
    val loop: Boolean = false,
) : HollowPacket {
    override fun handle(player: Player) {
        val data = CutsceneStorage.load(path)
        CutsceneCameraSystem.play(data, loop)
    }
}

@HollowPacketHandler(HollowPacketHandler.Direction.TO_CLIENT)
@Serializable
object StopCutscenePacket : HollowPacket {
    override fun handle(player: Player) {
        CutsceneCameraSystem.stop()
    }
}