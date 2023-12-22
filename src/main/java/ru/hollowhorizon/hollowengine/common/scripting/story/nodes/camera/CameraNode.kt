package ru.hollowhorizon.hollowengine.common.scripting.story.nodes.camera

import ru.hollowhorizon.hc.client.utils.nbt.NBTFormat
import ru.hollowhorizon.hc.client.utils.nbt.deserialize
import ru.hollowhorizon.hc.client.utils.nbt.loadAsNBT
import ru.hollowhorizon.hc.common.network.send
import ru.hollowhorizon.hollowengine.client.camera.CameraPath
import ru.hollowhorizon.hollowengine.client.screen.OverlayScreenPacket
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.IContextBuilder
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.base.wait
import ru.hollowhorizon.hollowengine.common.story.*


fun IContextBuilder.camera(time: () -> Int, path: () -> String) {
    +ru.hollowhorizon.hollowengine.common.scripting.story.nodes.base.SimpleNode {
        val nbt = DirectoryManager.HOLLOW_ENGINE.resolve("camera/${path()}").inputStream().loadAsNBT()

        val cameraPath = NBTFormat.deserialize<CameraPath>(nbt)
        cameraPath.time = time()
        stateMachine.team.onlineMembers.forEach {
            StartCameraPlayerPacket().send(cameraPath, it)
            OverlayScreenPacket().send(true, it)
        }
    }
    wait(time)
    +ru.hollowhorizon.hollowengine.common.scripting.story.nodes.base.SimpleNode {
        OverlayScreenPacket().send(false, *stateMachine.team.onlineMembers.toTypedArray())
    }
}