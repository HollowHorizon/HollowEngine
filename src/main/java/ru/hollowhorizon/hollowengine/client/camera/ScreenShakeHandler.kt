package ru.hollowhorizon.hollowengine.client.camera

import kotlinx.serialization.Serializable
import net.minecraft.client.Minecraft
import net.minecraft.util.Mth
import net.minecraft.util.RandomSource
import net.minecraft.world.entity.player.Player
import net.minecraftforge.client.event.RenderHandEvent
import net.minecraftforge.client.event.ViewportEvent.ComputeCameraAngles
import net.minecraftforge.eventbus.api.SubscribeEvent
import ru.hollowhorizon.hc.client.handlers.ClientTickHandler
import ru.hollowhorizon.hc.client.utils.math.Interpolation
import ru.hollowhorizon.hc.common.network.HollowPacketV2
import ru.hollowhorizon.hc.common.network.HollowPacketV3
import ru.hollowhorizon.hollowengine.mixins.CameraInvoker


object ScreenShakeHandler {
    private var startTime = 0
    var config = ScreenShakePacket()
    var enabled = false
        set(value) {
            startTime = ClientTickHandler.ticksNotPaused
            field = value
        }

    @SubscribeEvent
    fun cameraUpdate(event: ComputeCameraAngles) {
        if (!enabled) return
        val random = Minecraft.getInstance().player?.random ?: return

        val percent = (ClientTickHandler.ticksNotPaused - startTime + event.partialTick.toFloat()) / config.duration

        if (percent > 1f) {
            enabled = false
            return
        }

        val intensity = config.updateIntensity(percent)

        if (config.targets.contains(ShakeTarget.ROT)) {
            event.yaw += random.offset(intensity)
            event.pitch += random.offset(intensity)
            event.roll += random.offset(intensity)
        }

        if (config.targets.contains(ShakeTarget.POS)) {
            val inv = event.camera as CameraInvoker

            inv.invokeSetPosition(
                event.camera.position.add(
                    random.offset(intensity / 10).toDouble(),
                    random.offset(intensity / 10).toDouble(),
                    random.offset(intensity / 10).toDouble()
                )
            )
        }
    }

    @SubscribeEvent
    fun onHandRender(event: RenderHandEvent) {
        if (!enabled) return
        val random = Minecraft.getInstance().player?.random ?: return

        val percent = (ClientTickHandler.ticksNotPaused - startTime + event.partialTick) / config.duration
        val intensity = config.updateIntensity(percent)

        if (config.targets.contains(ShakeTarget.HAND)) {
            event.poseStack.translate(
                random.offset(intensity / 100).toDouble(),
                random.offset(intensity / 100).toDouble(),
                random.offset(intensity / 100).toDouble()
            )
        }
    }

    fun RandomSource.offset(intensity: Float): Float {
        return Mth.nextFloat(this, -intensity * 2, intensity * 2)
    }
}

enum class ShakeTarget {
    POS, ROT, HAND
}

@HollowPacketV2(HollowPacketV2.Direction.TO_CLIENT)
@Serializable
class ScreenShakePacket(
    var duration: Int = 20,
    var intensity1: Float = 3f,
    var intensity2: Float = 7f,
    var intensity3: Float = 4f,
    var intensityStart: Interpolation = Interpolation.LINEAR,
    var intensityEnd: Interpolation = Interpolation.LINEAR,
    var targets: List<ShakeTarget> = listOf(ShakeTarget.POS, ShakeTarget.ROT, ShakeTarget.HAND)
) : HollowPacketV3<ScreenShakePacket> {
    override fun handle(player: Player, data: ScreenShakePacket) {
        ScreenShakeHandler.config = data
        ScreenShakeHandler.enabled = true
    }

    fun updateIntensity(percent: Float): Float {
        return if (intensity2 != intensity3) {
            if (percent >= 0.5f) {
                Mth.lerp(intensityEnd.function(percent * 2 - 0.5f), intensity2, intensity3)
            } else {
                Mth.lerp(intensityStart.function(percent * 2), intensity1, intensity2)
            }
        } else {
            Mth.lerp(intensityStart.function(percent), intensity1, intensity2)
        }
    }
}