/*
 * MIT License
 *
 * Copyright (c) 2024 HollowHorizon
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package ru.hollowhorizon.hollowengine.client.camera

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.math.Vector3d
import kotlinx.serialization.Serializable
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.GameRenderer
import net.minecraft.client.renderer.RenderType
import net.minecraft.world.entity.player.Player
import net.minecraftforge.client.event.InputEvent
import net.minecraftforge.client.event.RenderGuiOverlayEvent
import net.minecraftforge.client.event.RenderLevelStageEvent
import net.minecraftforge.client.event.ViewportEvent
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay
import net.minecraftforge.eventbus.api.SubscribeEvent
import ru.hollowhorizon.hc.client.utils.colored
import ru.hollowhorizon.hc.client.utils.math.Interpolation
import ru.hollowhorizon.hc.client.utils.math.Spline3D
import ru.hollowhorizon.hc.client.utils.mc
import ru.hollowhorizon.hc.client.utils.mcText
import ru.hollowhorizon.hc.client.utils.nbt.ForVector3d
import ru.hollowhorizon.hc.client.utils.nbt.NBTFormat
import ru.hollowhorizon.hc.client.utils.nbt.save
import ru.hollowhorizon.hc.client.utils.nbt.serialize
import ru.hollowhorizon.hc.client.utils.plus
import ru.hollowhorizon.hc.common.network.HollowPacketV2
import ru.hollowhorizon.hc.common.network.HollowPacketV3
import ru.hollowhorizon.hollowengine.client.screen.SaveCameraPathScreen
import ru.hollowhorizon.hollowengine.client.screen.overlays.SelectingOverlay
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager
import ru.hollowhorizon.hollowengine.common.registry.ModItems
import ru.hollowhorizon.hollowengine.common.util.Keybind

object CameraHandler {
    private var xRot = 0f
    private var xRotO = 0f
    private var yRot = 0f
    private var yRotO = 0f
    private var zRot = 0f
    private var fov = 70.0
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
                for (i in 0..10 * points.size) {
                    val point = spline.getPoint(i / (10.0 * points.size))

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
    fun onRenderOverlay(event: RenderGuiOverlayEvent.Pre) {
        val player = mc.player ?: return
        if (player.mainHandItem.item != ModItems.CAMERA.get()) return
        if (event.overlay != VanillaGuiOverlay.HOTBAR.type()) return
        xRot += player.xRot - xRotO
        yRot += player.yHeadRot - yRotO
        Minecraft.getInstance().font.apply {
            drawShadow(
                event.poseStack,
                "rotation ".mcText + "x".mcText.colored(0xf23d30) + ": $xRot".mcText,
                5f,
                5f,
                0xFFFFFF
            )
            drawShadow(
                event.poseStack,
                "rotation ".mcText + "y".mcText.colored(0x52f26d) + ": $yRot".mcText,
                5f,
                14f,
                0xFFFFFF
            )
            drawShadow(
                event.poseStack,
                "rotation ".mcText + "z".mcText.colored(0x5285f2) + ": $zRot".mcText,
                5f,
                23f,
                0xFFFFFF
            )
            drawShadow(event.poseStack, "fov: $fov", 5f, 32f, 0xFFFFFF)
            drawShadow(event.poseStack, "point count: ${points.size}", 5f, 41f, 0xFFFFFF)
        }

        xRotO = player.xRot
        yRotO = player.yHeadRot

    }

    @SubscribeEvent
    fun onClicked(event: InputEvent.MouseButton.Post) {
        val player = mc.player ?: return
        if (mc.screen != null) return
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

        points.clear()

        Minecraft.getInstance().setScreen(SaveCameraPathScreen(path))
    }

    @SubscribeEvent
    fun onKeyPressed(event: InputEvent.Key) {
        if (mc.player?.mainHandItem?.item != ModItems.CAMERA.get()) return
        if (mc.screen != null) return
        if (event.action == 0) return

        val key = Keybind.fromCode(event.key)
        when (key) {
            Keybind.MINUS -> zRot--
            Keybind.EQUAL -> zRot++
            Keybind.LEFT_BRACKET -> {
                if (fov <= 1) fov -= 0.1
                else fov--
            }

            Keybind.RIGHT_BRACKET -> fov++
            Keybind.C -> {
                zRot = 0f
                fov = 70.0
            }

            else -> {}
        }
    }

    @SubscribeEvent
    fun onComputeAngles(event: ViewportEvent.ComputeCameraAngles) {
        val player = mc.player ?: return
        if (player.mainHandItem.item != ModItems.CAMERA.get()) return
        event.roll = zRot
    }

    @SubscribeEvent
    fun onComputeFov(event: ViewportEvent.ComputeFov) {
        val player = mc.player ?: return
        if (player.mainHandItem.item != ModItems.CAMERA.get()) return
        event.fov = fov
    }

    fun addPoint() {
        val player = mc.player ?: return
        val x = player.x
        val y = player.y + player.eyeHeight
        val z = player.z

        points += Point(x, y, z, xRot, yRot, zRot, fov)

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
    var time: Int = 0,
) {
    var interpolation = Interpolation.LINEAR
    val positions get() = points.map { Vector3d(startPos.x + it.x, startPos.y + it.y, startPos.z + it.z) }
    val rotations get() = points.map { it.rot }
}

@HollowPacketV2(HollowPacketV2.Direction.TO_SERVER)
@Serializable
class SaveOnServerPacket(private val path: CameraPath, private val fileName: String) :
    HollowPacketV3<SaveOnServerPacket> {
    override fun handle(player: Player, data: SaveOnServerPacket) {
        if (player.mainHandItem.item == ModItems.CAMERA.get()) {
            val name = if (!fileName.contains('.')) "$fileName.nbt" else fileName
            val file = DirectoryManager.HOLLOW_ENGINE.resolve("camera/$name.nbt")
            file.parentFile.mkdirs()

            NBTFormat.serialize(data.path).save(file.outputStream())
        }
    }

}