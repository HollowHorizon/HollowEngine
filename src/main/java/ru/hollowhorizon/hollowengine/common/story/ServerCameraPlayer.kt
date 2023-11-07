package ru.hollowhorizon.hollowengine.common.story

import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec3
import net.minecraftforge.client.event.EntityViewRenderEvent.CameraSetup
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.eventbus.api.SubscribeEvent
import ru.hollowhorizon.hc.HollowCore
import ru.hollowhorizon.hollowengine.mixins.CameraInvoker

class ServerCameraPlayer(val player: Player, val callback: () -> Unit = {}, vararg pairs: Pair<Int, CameraNode>): CameraPlayer(*pairs) {
    var isEnd = false

    fun start() {
        MinecraftForge.EVENT_BUS.register(this)
    }

    private fun updateMovement(event: CameraSetup) {
        if(!isEnd) {
            val (point, rotation) = update()
            HollowCore.LOGGER.info("Position: x: ${point.x}, y: ${point.y}, z: ${point.z}; Rotation: x: ${rotation.x}, y: ${rotation.y}")
            (event.camera as CameraInvoker).invokeSetPosition(Vec3(point.x, point.y, point.z))
            event.yaw = rotation.x
            event.pitch = rotation.y
        }
    }

    override fun onEnd() {
        callback()
        MinecraftForge.EVENT_BUS.unregister(this)
        isEnd = true
    }

    @SubscribeEvent
    fun onTick(event: CameraSetup) {
        this.updateMovement(event)
    }
}