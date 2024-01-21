package ru.hollowhorizon.hollowengine.common.scripting.story

import dev.ftb.mods.ftbteams.data.Team
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraft.world.phys.Vec3
import ru.hollowhorizon.hc.HollowCore
import ru.hollowhorizon.hollowengine.HollowEngine
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

private val Player.scriptName: String?
    get() = if (this.persistentData.contains("hs_name")) this.persistentData.getString("hs_name") else null

fun Team.randomPos(radius: Float = 10f): Vec3 {
    val player = if(onlineMembers.isNotEmpty()) onlineMembers.random() else return Vec3(0.0, 0.0, 0.0)
    val pos = player.position()
    val randomAngle = Math.toRadians(Math.random() * 360.0).toFloat()

    val xOffset = radius * cos(randomAngle)
    val zOffset = radius * sin(randomAngle)

    val y =
        player.level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, (pos.x + xOffset).toInt(), (pos.z + zOffset).toInt())

    return Vec3(pos.x + xOffset, y.toDouble(), pos.z + zOffset)
}