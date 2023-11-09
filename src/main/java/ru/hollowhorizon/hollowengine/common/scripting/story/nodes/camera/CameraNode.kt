package ru.hollowhorizon.hollowengine.common.scripting.story.nodes.camera

import com.mojang.math.Vector3d
import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3
import ru.hollowhorizon.hc.client.utils.math.Interpolation
import ru.hollowhorizon.hc.common.network.send
import ru.hollowhorizon.hollowengine.client.screen.OverlayScreenPacket
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.IContextBuilder
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.base.WaitNode
import ru.hollowhorizon.hollowengine.common.story.*

class CameraPath {
    val cameraNodes = ArrayList<Pair<Int, CameraNode>>()
    val time get() = cameraNodes.sumOf { it.first }
    fun spline(time: Int, startRot: Vec2, endRot: Vec2, vararg points: Vec3, interpolation: Interpolation = Interpolation.LINEAR) {
        cameraNodes.add(
            Pair(
                time,
                SplineNode(startRot, endRot, *points.map { Vector3d(it.x(), it.y(), it.z()) }.toTypedArray(), interpolation=interpolation)
            )
        )
    }

    fun point(time: Int, startRot: Vec2, endRot: Vec2, point1: Vec3, point2: Vec3) {
        cameraNodes.add(
            Pair(
                time,
                SimpleNode(
                    Vector3d(point1.x(), point1.y(), point1.z()),
                    Vector3d(point2.x(), point2.y(), point2.z()),
                    startRot,
                    endRot
                )
            )
        )
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
        Vec3(node.lastPos.x, node.lastPos.y, node.lastPos.z)
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