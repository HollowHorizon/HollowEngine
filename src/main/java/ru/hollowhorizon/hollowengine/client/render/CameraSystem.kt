package ru.hollowhorizon.hollowengine.client.render

import net.minecraft.client.Minecraft
import net.minecraft.util.Mth
import ru.hollowhorizon.hollowengine.common.events.SubscribeEvent
import ru.hollowhorizon.hollowengine.common.utils.get
import ru.hollowhorizon.hollowengine.common.capability.Camera
import ru.hollowhorizon.hollowengine.common.capability.CameraCapability
import ru.hollowhorizon.hollowengine.mixins.client.CameraInvoker
import kotlin.math.sqrt

@SubscribeEvent
fun onSetup(event: CameraSetupEvent) {
    val player = Minecraft.getInstance().player ?: return
    val camera = player[CameraCapability::class].camera
    val controller = event.camera as CameraInvoker

    when (camera) {
        Camera.Default -> return
        is Camera.Static -> {
            controller.`hollowcore$setPosition`(camera.pos.x, camera.pos.y, camera.pos.z)
            event.yaw = camera.yaw
            event.pitch = camera.pitch
            event.roll = camera.roll
        }

        is Camera.Watcher -> {
            controller.`hollowcore$setPosition`(camera.pos.x, camera.pos.y, camera.pos.z)
            val target = camera.entity.getPosition(event.partialTick).add(0.0, camera.entity.eyeHeight.toDouble(), 0.0)
            val d0: Double = target.x - camera.pos.x
            val d1: Double = target.y - camera.pos.y
            val d2: Double = target.z - camera.pos.z
            val d3 = sqrt(d0 * d0 + d2 * d2)
            event.pitch = Mth.wrapDegrees((-(Mth.atan2(d1, d3) * (180f / Mth.PI))).toFloat())
            event.yaw = Mth.wrapDegrees((Mth.atan2(d2, d0) * (180f / Mth.PI)).toFloat() - 90.0f)
        }
    }
}