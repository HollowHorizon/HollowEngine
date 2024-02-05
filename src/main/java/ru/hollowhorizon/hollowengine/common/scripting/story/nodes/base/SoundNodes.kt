package ru.hollowhorizon.hollowengine.common.scripting.story.nodes.base

import net.minecraft.network.protocol.game.ClientboundCustomSoundPacket
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket
import net.minecraft.sounds.SoundSource
import net.minecraft.world.phys.Vec3
import ru.hollowhorizon.hc.client.utils.rl
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.IContextBuilder

class SoundContainer {
    var sound = ""
    var volume = 1.0f
    var pitch = 1.0f
    var pos: Vec3? = null
}

fun IContextBuilder.playSound(sound: SoundContainer.() -> Unit) = +SimpleNode {
    val container = SoundContainer().apply(sound)
    stateMachine.team.onlineMembers.forEach {
        it.connection.send(
            ClientboundCustomSoundPacket(
                container.sound.rl,
                SoundSource.MASTER,
                container.pos ?: it.position(),
                container.volume,
                container.pitch,
                it.random.nextLong()
            )
        )
    }
}

fun IContextBuilder.stopSound(sound: () -> String) = +SimpleNode {
    stateMachine.team.onlineMembers.forEach {
        it.connection.send(
            ClientboundStopSoundPacket(
                sound().rl,
                SoundSource.MASTER
            )
        )
    }
}