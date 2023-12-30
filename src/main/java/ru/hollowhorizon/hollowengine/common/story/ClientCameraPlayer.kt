package ru.hollowhorizon.hollowengine.common.story

import kotlinx.serialization.Serializable
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec3
import net.minecraftforge.client.event.EntityViewRenderEvent
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.eventbus.api.SubscribeEvent
import ru.hollowhorizon.hc.client.utils.nbt.ForCompoundNBT
import ru.hollowhorizon.hc.common.network.HollowPacketV2
import ru.hollowhorizon.hc.common.network.HollowPacketV3
import ru.hollowhorizon.hollowengine.client.camera.CameraPath
import ru.hollowhorizon.hollowengine.mixins.CameraInvoker

@Serializable
class Container(val tag: @Serializable(ForCompoundNBT::class) CompoundTag)

@HollowPacketV2(HollowPacketV2.Direction.TO_CLIENT)
@Serializable
class StartCameraPlayerPacket(val path: CameraPath) : HollowPacketV3<StartCameraPlayerPacket> {
    override fun handle(player: Player, data: StartCameraPlayerPacket) {
        ClientCameraPlayer.start(data.path)
    }

}

object ClientCameraPlayer : CameraPlayer() {

    fun start(path: CameraPath) {
        this.path = SplineNode(path, path.interpolation)
        this.maxTime = path.time
        reset()
        MinecraftForge.EVENT_BUS.register(this)
    }

    @SubscribeEvent
    fun updateMovement(event: EntityViewRenderEvent.CameraSetup) {
        val (point, rotation) = update()
        (event.camera as CameraInvoker).invokeSetPosition(Vec3(point.x, point.y, point.z))
        event.pitch = rotation.x()
        event.yaw = rotation.y()
        event.roll = rotation.z()
    }

    override fun onEnd() {
        MinecraftForge.EVENT_BUS.unregister(this)
    }
}