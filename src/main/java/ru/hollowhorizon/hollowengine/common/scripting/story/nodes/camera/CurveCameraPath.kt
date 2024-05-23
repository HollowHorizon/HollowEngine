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

package ru.hollowhorizon.hollowengine.common.scripting.story.nodes.camera

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.*
import com.mojang.math.Vector3f
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.renderer.GameRenderer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.util.Mth
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec3
import net.minecraftforge.client.event.RenderGuiOverlayEvent
import net.minecraftforge.client.event.ViewportEvent
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.eventbus.api.SubscribeEvent
import ru.hollowhorizon.hc.HollowCore
import ru.hollowhorizon.hc.api.utils.Polymorphic
import ru.hollowhorizon.hc.client.handlers.TickHandler
import ru.hollowhorizon.hc.client.utils.math.Interpolation
import ru.hollowhorizon.hc.client.utils.math.Spline
import ru.hollowhorizon.hc.client.utils.math.Spline3D
import ru.hollowhorizon.hc.client.utils.mc
import ru.hollowhorizon.hollowengine.client.camera.CameraHandler
import ru.hollowhorizon.hollowengine.client.camera.CameraPath
import ru.hollowhorizon.hollowengine.common.registry.ModItems
import ru.hollowhorizon.hollowengine.mixins.CameraInvoker

@Serializable
@Polymorphic(ICameraPath::class)
class CurveCameraPath(
    override val maxTime: Int,
    val path: CameraPath,
    val interpolation: Interpolation,
    val boarders: Boolean = true,
    val boarderInterpolation: Interpolation = Interpolation.LINEAR,
    val rotationInterpolation: Interpolation = Interpolation.LINEAR
) :
    ICameraPath {
    @Transient
    var startTime = TickHandler.currentTicks

    @Transient
    var spline = Spline3D(path.positions, path.rotations)


    override fun reset() {
        startTime = TickHandler.currentTicks
    }

    override fun serverUpdate(players: List<Player>) {
        val time = TickHandler.currentTicks - startTime
        spline.getPoint(interpolation(time / maxTime.toFloat()).toDouble()).apply {
            if (this.x.isNaN() || this.y.isNaN() || this.z.isNaN()) {
                HollowCore.LOGGER.warn("NaN in spline: {}, {}, {}", x, y, z)
                return
            }
            players.forEach {
                it.moveTo(this.x, this.y, this.z)
            }
        }
        HollowCore.LOGGER.info("server: {}/{}", TickHandler.currentTicks - startTime, maxTime)
    }

    override fun onStartClient() {
        MinecraftForge.EVENT_BUS.register(this)
    }

    @SubscribeEvent
    fun updateCamera(event: ViewportEvent.ComputeCameraAngles) {
        val time = TickHandler.currentTicks - startTime + Minecraft.getInstance().partialTick
        val factor = (time / maxTime).coerceAtLeast(0f)

        if (factor > 1) MinecraftForge.EVENT_BUS.unregister(this)

        val interpolated = interpolation(factor).toDouble()
        val point = spline.getPoint(interpolated)

        val rotations = path.rotations

        val index = ((rotations.size - 1) * factor).toInt()
        val current = rotations[index]
        val next = if(index == rotations.size - 1) current else rotations[index + 1]

        val percent = rotationInterpolation( ((rotations.size - 1) * factor) % 1.0f )

        val rotation = Vector3f(
            Mth.lerp(percent, current.x(), next.x()),
            Mth.lerp(percent, current.y(), next.y()),
            Mth.lerp(percent, current.z(), next.z()),
        )

        //val rotation = spline.getRotation(interpolated)

        if (point.x.isNaN() || point.y.isNaN() || point.z.isNaN()) {
            HollowCore.LOGGER.warn("NaN in spline: {}, {}, {}, progress: {}", point.x, point.y, point.z, interpolated)
        } else {
            (event.camera as CameraInvoker).invokeSetPosition(Vec3(point.x, point.y, point.z))
        }
        event.yaw = rotation.y()
        event.pitch = rotation.x()
        event.roll = rotation.z()
    }

    @SubscribeEvent
    fun onComputeFov(event: ViewportEvent.ComputeFov) {
        val time = TickHandler.currentTicks - startTime + Minecraft.getInstance().partialTick
        val factor = (time / maxTime).coerceIn(0f, 1f)

        val interpolated = interpolation(factor).toDouble()
        val point = spline.getPoint(interpolated)

        val rotations = path.points

        val index = ((rotations.size - 1) * factor).toInt()
        val current = rotations[index].fov
        val next = if(index == rotations.size - 1) current else rotations[index + 1].fov

        event.fov = Mth.lerp(((rotations.size - 1) * factor) % 1.0, current, next)
    }

    @SubscribeEvent
    fun onOverlay(event: RenderGuiOverlayEvent.Pre) {
        if (event.overlay != VanillaGuiOverlay.HOTBAR.type()) return

        if (boarders) {

            val width = event.window.guiScaledWidth
            val height = event.window.guiScaledHeight
            val time = TickHandler.currentTicks - startTime + Minecraft.getInstance().partialTick

            val totalTicks = 10 // Общее количество тиков для анимации

            val factor: Float = when {
                time < totalTicks -> time / totalTicks
                time > maxTime - totalTicks -> (maxTime - time) / totalTicks
                else -> 1f
            }.coerceIn(0f, 1f)

            val interpolated = boarderInterpolation(factor)

            Screen.fill(event.poseStack, 0, 0, width, ((height / 10) * interpolated).toInt(), 0xFF000000.toInt())
            Screen.fill(
                event.poseStack,
                0,
                (height - (height / 10) * interpolated).toInt(),
                width,
                height,
                0xFF000000.toInt()
            )
        }
    }

    override val isEnd get() = TickHandler.currentTicks - startTime >= maxTime
}