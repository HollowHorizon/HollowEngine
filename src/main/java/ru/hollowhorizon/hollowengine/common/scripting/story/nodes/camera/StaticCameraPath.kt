package ru.hollowhorizon.hollowengine.common.scripting.story.nodes.camera

import com.mojang.math.Vector3f
import dev.ftb.mods.ftbteams.data.Team
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.minecraft.client.Minecraft
import net.minecraft.world.phys.Vec3
import net.minecraftforge.client.event.ViewportEvent.ComputeCameraAngles
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.eventbus.api.SubscribeEvent
import ru.hollowhorizon.hc.api.utils.Polymorphic
import ru.hollowhorizon.hc.client.handlers.TickHandler
import ru.hollowhorizon.hc.client.utils.nbt.ForVec3
import ru.hollowhorizon.hc.client.utils.nbt.ForVector3f
import ru.hollowhorizon.hollowengine.common.scripting.forEachPlayer

@Serializable
@Polymorphic(ICameraPath::class)
class StaticCameraPath(
    override val maxTime: Int,
    val pos: @Serializable(ForVec3::class) Vec3,
    val rotation: @Serializable(ForVector3f::class) Vector3f
) : ICameraPath {
    @Transient
    var startTime = TickHandler.currentTicks()
    override val isEnd get() = TickHandler.currentTicks() - startTime >= maxTime

    override fun reset() {
        startTime = TickHandler.currentTicks()
    }

    override fun serverUpdate(team: Team) {
        team.forEachPlayer { it.teleportTo(it.getLevel(), pos.x, pos.y, pos.z, it.yHeadRot, it.xRot) }
    }

    override fun onStartClient() {
        super.onStartClient()
        MinecraftForge.EVENT_BUS.register(this)
    }

    @SubscribeEvent
    fun onCameraSetup(event: ComputeCameraAngles){
        if(isEnd) MinecraftForge.EVENT_BUS.unregister(this)

        event.yaw = rotation.y()
        event.pitch = rotation.x()
        event.roll = rotation.z()
    }
}