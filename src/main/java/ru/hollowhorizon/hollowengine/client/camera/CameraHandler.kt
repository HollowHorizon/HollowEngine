package ru.hollowhorizon.hollowengine.client.camera

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.math.Vector3d
import kotlinx.serialization.Serializable
import net.minecraft.client.renderer.GameRenderer
import net.minecraft.client.renderer.RenderType
import net.minecraft.world.entity.player.Player
import net.minecraftforge.client.event.EntityViewRenderEvent.CameraSetup
import net.minecraftforge.client.event.InputEvent
import net.minecraftforge.client.event.RenderLevelStageEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import ru.hollowhorizon.hc.client.math.Spline3D
import ru.hollowhorizon.hc.client.utils.math.Interpolation
import ru.hollowhorizon.hc.client.utils.mc
import ru.hollowhorizon.hc.client.utils.nbt.ForVector3d
import ru.hollowhorizon.hc.client.utils.nbt.NBTFormat
import ru.hollowhorizon.hc.client.utils.nbt.save
import ru.hollowhorizon.hc.client.utils.nbt.serialize
import ru.hollowhorizon.hc.common.network.HollowPacketV2
import ru.hollowhorizon.hc.common.network.HollowPacketV3
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager
import ru.hollowhorizon.hollowengine.common.registry.ModItems
import ru.hollowhorizon.hollowengine.common.util.Keybind

object CameraHandler {
    private var zRot = 0f
    private val points = ArrayList<Point>()
    private lateinit var spline: Spline3D

    @SubscribeEvent
    fun onRenderWorldLast(event: RenderLevelStageEvent) {
        if (event.stage != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return
        if (mc.player?.mainHandItem?.item != ModItems.CAMERA.get()) return
        val p = mc.gameRenderer.mainCamera.position
        val stack = event.poseStack

        stack.pushPose()
        stack.translate(-p.x, -p.y, -p.z)

        if (points.size > 1) {
            val shader = RenderSystem.getShader()
            RenderSystem.setShader(GameRenderer::getPositionColorShader)
            val matrix = stack.last().pose()
            val source = mc.renderBuffers().bufferSource()
            RenderSystem.lineWidth(10f)
            source.getBuffer(RenderType.LINES).apply {
                var lastPoint = spline.getPoint(0.0)
                for (i in 0..10*points.size) {
                    val point = spline.getPoint(i / (10.0*points.size))

                    vertex(matrix, lastPoint.x.toFloat(), lastPoint.y.toFloat(), lastPoint.z.toFloat())
                    color(0.16f, 0.83f, 0f, 0.6f)
                    normal(0f, 0f, 0f)
                    endVertex()

                    vertex(matrix, point.x.toFloat(), point.y.toFloat(), point.z.toFloat())
                    color(0.16f, 0.83f, 0f, 0.6f)
                    normal(0f, 0f, 0f)
                    endVertex()

                    lastPoint = point
                }

            }
            source.endBatch(RenderType.LINES)
            RenderSystem.setShader { shader }
            RenderSystem.lineWidth(1f)
        }

        stack.popPose()
    }

    @SubscribeEvent
    fun onClicked(event: InputEvent.MouseInputEvent) {
        val player = mc.player ?: return
        if (player.mainHandItem.item != ModItems.CAMERA.get() || event.action != 0) return

        when (event.button) {
            0 -> removePoint()
            1 -> {
                if (player.isShiftKeyDown) save()
                else addPoint()
            }
        }
    }

    private fun save() {
        if (points.size < 2) return

        val startPoint = points[0].pos

        val path = CameraPath(startPoint, points.map { it.toLocal(startPoint) })

        SaveOnServerPacket(path).send()

        points.clear()
    }

    @SubscribeEvent
    fun onKeyPressed(event: InputEvent.KeyInputEvent) {
        if (mc.player?.mainHandItem?.item != ModItems.CAMERA.get()) return

        val key = Keybind.fromCode(event.key)
        when (key) {
            Keybind.MINUS -> zRot--
            Keybind.EQUAL -> zRot++
            Keybind.C -> zRot = 0f
            else -> {}
        }
    }

    @SubscribeEvent
    fun onComputeAngles(event: CameraSetup) {
        if (mc.player?.mainHandItem?.item != ModItems.CAMERA.get()) return
        event.roll = zRot
    }

    fun addPoint() {
        val player = mc.player ?: return
        val x = player.x
        val y = player.y + player.eyeHeight
        val z = player.z
        val xRot = player.getViewXRot(mc.frameTime)
        val yRot = player.getViewYRot(mc.frameTime)

        points += Point(x, y, z, xRot, yRot, zRot)

        if (points.size > 1) {
            spline = Spline3D(
                points.map { it.pos },
                points.map { it.rot }
            )
        }
    }

    fun removePoint() {
        val player = mc.player ?: return
        val point = points.minByOrNull { player.position().distanceToSqr(it.x, it.y, it.z) } ?: return

        points.remove(point)

        if (points.size > 1) {
            spline = Spline3D(
                points.map { it.pos },
                points.map { it.rot }
            )
        }
    }
}

@Serializable
class CameraPath(
    val startPos: @Serializable(ForVector3d::class) Vector3d,
    val points: List<Point>,
    var time: Int = 0
) {
    var interpolation = Interpolation.LINEAR
    val positions get() = points.map { Vector3d(startPos.x + it.x, startPos.y + it.y, startPos.z + it.z) }
    val rotations get() = points.map { it.rot }
}

@HollowPacketV2(HollowPacketV2.Direction.TO_SERVER)
@Serializable
class SaveOnServerPacket(private val path: CameraPath) : HollowPacketV3<SaveOnServerPacket> {
    override fun handle(player: Player, data: SaveOnServerPacket) {
        if (player.mainHandItem.item == ModItems.CAMERA.get()) {
            val file = DirectoryManager.HOLLOW_ENGINE.resolve("camera/${Integer.toHexString(data.path.hashCode())}.nbt")
            file.parentFile.mkdirs()

            NBTFormat.serialize(data.path).save(file.outputStream())
        }
    }

}