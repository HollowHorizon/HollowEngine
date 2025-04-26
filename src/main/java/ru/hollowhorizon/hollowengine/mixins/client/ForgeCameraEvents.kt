package ru.hollowhorizon.hollowengine.mixins.client

//? if forge {
/*import net.minecraftforge.client.event.ViewportEvent.ComputeCameraAngles
import net.minecraftforge.common.MinecraftForge
import ru.hollowhorizon.hc.common.events.EventBus
import ru.hollowhorizon.hollowengine.client.render.CameraSetupEvent

fun setupCamera() {
    MinecraftForge.EVENT_BUS.addListener<ComputeCameraAngles> {
        val event = CameraSetupEvent(it.renderer, it.camera, it.partialTick.toFloat(), it.yaw, it.pitch, it.roll)
        EventBus.post(event)
        it.yaw = event.yaw
        it.pitch = event.pitch
        it.roll = event.roll
    }
}
*///?}