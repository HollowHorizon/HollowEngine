package ru.hollowhorizon.hollowengine.common.scripting.story.nodes.camera

import dev.ftb.mods.ftbteams.data.Team
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.world.phys.Vec3
import net.minecraftforge.client.event.RenderGuiOverlayEvent
import net.minecraftforge.client.event.ViewportEvent
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.eventbus.api.Event
import net.minecraftforge.eventbus.api.SubscribeEvent
import ru.hollowhorizon.hc.api.utils.Polymorphic
import ru.hollowhorizon.hc.client.handlers.ClientTickHandler
import ru.hollowhorizon.hc.client.math.Spline3D
import ru.hollowhorizon.hc.client.utils.math.Interpolation
import ru.hollowhorizon.hollowengine.client.camera.CameraPath
import ru.hollowhorizon.hollowengine.common.scripting.forEachPlayer
import ru.hollowhorizon.hollowengine.mixins.CameraInvoker

@Serializable
@Polymorphic(ICameraPath::class)
class CurveCameraPath(override val maxTime: Int, val path: CameraPath, val interpolation: Interpolation, val boarders: Boolean = true, val boarderInterpolation: Interpolation = Interpolation.LINEAR) :
    ICameraPath {
    @Transient
    var startTime = ClientTickHandler.currentTicks()

    @Transient
    var spline = Spline3D(path.positions, path.rotations)

    override fun reset() {
        startTime = ClientTickHandler.currentTicks()
    }

    override fun serverUpdate(team: Team) {
        val time = ClientTickHandler.currentTicks() - startTime
        spline.getPoint(interpolation(time / maxTime.toFloat()).toDouble()).apply {
            team.forEachPlayer {
                it.moveTo(this.x, this.y, this.z)
            }
        }
    }

    override fun onStartClient() {
        MinecraftForge.EVENT_BUS.register(this)
    }

    @SubscribeEvent
    fun updateCamera(event: ViewportEvent.ComputeCameraAngles) {
        val time = ClientTickHandler.currentTicks() - startTime + Minecraft.getInstance().partialTick
        val factor = (time / maxTime).coerceAtLeast(0f)

        if (factor > 1) MinecraftForge.EVENT_BUS.unregister(this)

        val interpolated = interpolation(factor).toDouble()
        val point = spline.getPoint(interpolated)
        val rotation = spline.getRotation(interpolated)

        (event.camera as CameraInvoker).invokeSetPosition(Vec3(point.x, point.y, point.z))
        event.yaw = rotation.y()
        event.pitch = rotation.x()
        event.roll = rotation.z()
    }

    @SubscribeEvent
    fun onOverlay(event: RenderGuiOverlayEvent.Pre) {
        if (event.overlay != VanillaGuiOverlay.VIGNETTE.type()) return

        val width = event.window.guiScaledWidth
        val height = event.window.guiScaledHeight
        val time = ClientTickHandler.currentTicks() - startTime + Minecraft.getInstance().partialTick

        val totalTicks = 10 // Общее количество тиков для анимации

        val factor: Float = when {
            time < totalTicks -> time / totalTicks
            time > maxTime - totalTicks -> (maxTime - time) / totalTicks
            else -> 1f
        }.coerceIn(0f, 1f)

        val interpolated = boarderInterpolation(factor)

        Screen.fill(event.poseStack, 0, 0, width, ((height / 10) * interpolated).toInt(), 0xFF000000.toInt())
        Screen.fill(event.poseStack, 0, (height - (height / 10) * interpolated).toInt(), width, height, 0xFF000000.toInt())
    }

    override val isEnd get() = ClientTickHandler.currentTicks() - startTime >= maxTime
}