package ru.hollowhorizon.hollowengine.common.scripting.story.nodes.camera

import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3
import ru.hollowhorizon.hc.common.network.send
import ru.hollowhorizon.hollowengine.client.screen.OverlayScreenPacket
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.IContextBuilder
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.base.WaitNode
import ru.hollowhorizon.hollowengine.common.story.*
import thedarkcolour.kotlinforforge.forge.vectorutil.toVec3
import thedarkcolour.kotlinforforge.forge.vectorutil.toVector3d

class CameraPath {
    val cameraNodes = ArrayList<Pair<Int, CameraNode>>()
    val time get() = cameraNodes.sumOf { it.first }
    fun spline(time: Int, startRot: Vec2, endRot: Vec2, vararg points: Vec3) {
        cameraNodes.add(Pair(time, SplineNode(startRot, endRot, *points.map { it.toVector3d() }.toTypedArray())))
    }

    fun point(time: Int, startRot: Vec2, endRot: Vec2, point1: Vec3, point2: Vec3) {
        cameraNodes.add(Pair(time, SimpleNode(point1.toVector3d(), point2.toVector3d(), startRot, endRot)))
    }
}

fun IContextBuilder.createCameraPath(body: CameraPath.() -> Unit) {
    +WaitNode {
        val path = CameraPath().apply(body)
        stateMachine.team.onlineMembers.forEach {
            StartCameraPlayerPacket().send(Container(CameraPlayer(*path.cameraNodes.toTypedArray()).serializeNBT()), it)
            OverlayScreenPacket().send(true, it)
        }
        path.time
    }
    stateMachine.team tp {
        val path = CameraPath().apply(body)
        val node = path.cameraNodes.last().second
        node.lastPos.toVec3()
    }
    +ru.hollowhorizon.hollowengine.common.scripting.story.nodes.base.SimpleNode {
        stateMachine.team.onlineMembers.forEach {
            OverlayScreenPacket().send(false, it)
        }
    }
}

fun IContextBuilder.main() {
    createCameraPath {
        spline(
            10,
            vec(1, 2), vec(3, 4),
            pos(1, 2, 3),
            pos(4, 5, 6),
            pos(7, 8, 9),
        )
        point(
            10, vec(1, 2), vec(3, 4),
            pos(4, 5, 6),
            pos(7, 8, 9)
        )
    }
}