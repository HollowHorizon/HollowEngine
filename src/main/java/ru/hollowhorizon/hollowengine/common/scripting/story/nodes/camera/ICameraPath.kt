package ru.hollowhorizon.hollowengine.common.scripting.story.nodes.camera

import dev.ftb.mods.ftbteams.data.Team
import net.minecraft.world.level.GameType
import net.minecraftforge.network.PacketDistributor
import ru.hollowhorizon.hollowengine.common.scripting.forEachPlayer

interface ICameraPath {
    val maxTime: Int
    fun serverUpdate(team: Team)

    fun onStartServer(team: Team) {
        team.forEachPlayer {
            it.setGameMode(GameType.SPECTATOR)
            CameraPathPacket(this).send(PacketDistributor.PLAYER.with { it })
        }
    }

    fun onStartClient() {

    }

    fun reset()

    val isEnd: Boolean
}