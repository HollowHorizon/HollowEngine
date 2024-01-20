package ru.hollowhorizon.hollowengine.common.scripting.story.nodes.camera

import net.minecraftforge.network.PacketDistributor
import ru.hollowhorizon.hc.client.utils.math.Interpolation
import ru.hollowhorizon.hc.client.utils.nbt.NBTFormat
import ru.hollowhorizon.hc.client.utils.nbt.deserialize
import ru.hollowhorizon.hc.client.utils.nbt.loadAsNBT
import ru.hollowhorizon.hc.common.network.send
import ru.hollowhorizon.hollowengine.client.camera.CameraPath
import ru.hollowhorizon.hollowengine.client.camera.ScreenShakePacket
import ru.hollowhorizon.hollowengine.client.screen.OverlayScreenPacket
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.IContextBuilder
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.base.SimpleNode
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.base.wait
import ru.hollowhorizon.hollowengine.common.story.*


class CameraContainer {
    var time = 200
    var interpolation = Interpolation.LINEAR
    var path = ""
}

fun IContextBuilder.camera(body: CameraContainer.() -> Unit) {
    +SimpleNode {
        val container = CameraContainer().apply(body)
        if(container.path.isEmpty()) return@SimpleNode
        val nbt = DirectoryManager.HOLLOW_ENGINE.resolve("camera/${container.path}").inputStream().loadAsNBT()

        val cameraPath = NBTFormat.deserialize<CameraPath>(nbt)
        cameraPath.time = container.time
        cameraPath.interpolation = container.interpolation
        stateMachine.team.onlineMembers.forEach {
            StartCameraPlayerPacket(cameraPath).send(PacketDistributor.PLAYER.with {it})
            OverlayScreenPacket(true).send(PacketDistributor.PLAYER.with {it})
        }
    }
    wait {
        val container = CameraContainer().apply(body)
        container.time
    }
    +SimpleNode {
        stateMachine.team.onlineMembers.forEach {
            OverlayScreenPacket(false).send(PacketDistributor.PLAYER.with {it})
        }
    }
}

fun IContextBuilder.shake(config: ScreenShakePacket.() -> Unit) = +SimpleNode {
    val packet = ScreenShakePacket().apply(config)

    stateMachine.team.onlineMembers.forEach {
        packet.send(PacketDistributor.PLAYER.with {it})
    }
}