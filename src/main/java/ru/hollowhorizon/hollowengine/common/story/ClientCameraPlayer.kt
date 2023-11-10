package ru.hollowhorizon.hollowengine.common.story

import kotlinx.serialization.Serializable
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.phys.Vec3
import net.minecraftforge.client.event.EntityViewRenderEvent
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.network.NetworkDirection
import ru.hollowhorizon.hc.client.utils.nbt.ForCompoundNBT
import ru.hollowhorizon.hc.common.network.HollowPacketV2
import ru.hollowhorizon.hc.common.network.Packet
import ru.hollowhorizon.hollowengine.mixins.CameraInvoker

@Serializable
class Container(val tag: @Serializable(ForCompoundNBT::class) CompoundTag)

@HollowPacketV2(NetworkDirection.PLAY_TO_CLIENT)
class StartCameraPlayerPacket: Packet<Container>({ player, container ->
    ClientCameraPlayer.start(container.tag)
})

object ClientCameraPlayer : CameraPlayer() {

    fun start(nbt: CompoundTag) {
        deserializeNBT(nbt)
        reset()
        MinecraftForge.EVENT_BUS.register(this)
    }

    @SubscribeEvent
    fun updateMovement(event: EntityViewRenderEvent.CameraSetup) {
        val (point, rotation) = update()
        (event.camera as CameraInvoker).invokeSetPosition(Vec3(point.x, point.y, point.z))
        event.yaw = rotation.x
        event.pitch = rotation.y
    }

    override fun onEnd() {
        MinecraftForge.EVENT_BUS.unregister(this)
    }
}