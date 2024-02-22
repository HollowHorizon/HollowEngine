package ru.hollowhorizon.hollowengine.common.scripting.story.nodes.camera

import dev.ftb.mods.ftbteams.data.Team
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.minecraft.client.Minecraft
import net.minecraft.util.Mth
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.Vec3
import net.minecraftforge.client.event.ViewportEvent
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.eventbus.api.SubscribeEvent
import ru.hollowhorizon.hc.api.utils.Polymorphic
import ru.hollowhorizon.hc.client.handlers.ClientTickHandler
import ru.hollowhorizon.hc.client.utils.nbt.ForEntity
import ru.hollowhorizon.hc.client.utils.nbt.ForVec3
import ru.hollowhorizon.hollowengine.common.scripting.forEachPlayer
import ru.hollowhorizon.hollowengine.mixins.CameraInvoker
import kotlin.math.sqrt

@Serializable
@Polymorphic(ICameraPath::class)
class EntityCameraPath(
    override val maxTime: Int,
    val pos: @Serializable(ForVec3::class) Vec3,
    val entity: @Serializable(with = ForEntity::class) Entity
) : ICameraPath {
    @Transient
    var startTime = ClientTickHandler.currentTicks()
    override fun serverUpdate(team: Team) {
        team.forEachPlayer { team.forEachPlayer { it.moveTo(pos.x, pos.y, pos.z) } }
    }

    override fun onStartServer(team: Team) {
        team.forEachPlayer { team.forEachPlayer { it.moveTo(pos.x, pos.y, pos.z) } }
        super.onStartServer(team)
    }

    override fun reset() {
        startTime = ClientTickHandler.currentTicks()
    }

    override fun onStartClient() {
        Minecraft.getInstance().player?.moveTo(pos)
        MinecraftForge.EVENT_BUS.register(this)
    }

    @SubscribeEvent
    fun updateCamera(event: ViewportEvent.ComputeCameraAngles) {
        val partialTick = Minecraft.getInstance().partialTick
        val time = ClientTickHandler.currentTicks() - startTime + partialTick
        val factor = time / maxTime

        if (factor > 1) MinecraftForge.EVENT_BUS.unregister(this)

        (event.camera as CameraInvoker).invokeSetPosition(pos)
        Minecraft.getInstance().player?.let {
            val player = it.getPosition(partialTick).add(0.0, it.eyeHeight.toDouble(), 0.0)
            val target = entity.getPosition(partialTick).add(0.0, entity.eyeHeight.toDouble(), 0.0)
            val d0: Double = target.x - player.x
            val d1: Double = target.y - player.y
            val d2: Double = target.z - player.z
            val d3 = sqrt(d0 * d0 + d2 * d2)
            event.pitch = Mth.wrapDegrees((-(Mth.atan2(d1, d3) * (180f / Math.PI))).toFloat())
            event.yaw = Mth.wrapDegrees((Mth.atan2(d2, d0) * (180f / Math.PI)).toFloat() - 90.0f)
        }
    }

    override val isEnd get() = ClientTickHandler.currentTicks() - startTime >= maxTime
}