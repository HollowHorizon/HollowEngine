/*
 * MIT License
 *
 * Copyright (c) 2024 HollowHorizon
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package ru.hollowhorizon.hollowengine.common.scripting.story.nodes.camera

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.minecraft.client.Minecraft
import net.minecraft.server.level.ServerPlayer
import net.minecraft.util.Mth
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec3
import net.minecraftforge.client.event.ViewportEvent
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.eventbus.api.SubscribeEvent
import ru.hollowhorizon.hc.api.utils.Polymorphic
import ru.hollowhorizon.hc.client.handlers.TickHandler
import ru.hollowhorizon.hc.client.utils.nbt.ForEntity
import ru.hollowhorizon.hc.client.utils.nbt.ForVec3
import ru.hollowhorizon.hollowengine.mixins.CameraInvoker
import kotlin.math.sqrt

@Serializable
@Polymorphic(ICameraPath::class)
class EntityCameraPath(
    override val maxTime: Int,
    val pos: @Serializable(ForVec3::class) Vec3,
    val entity: @Serializable(with = ForEntity::class) Entity,
) : ICameraPath {
    @Transient
    var startTime = TickHandler.currentTicks
    override fun serverUpdate(players: List<Player>) {
        players.forEach { it.moveTo(pos.x, pos.y, pos.z) }
    }


    override fun onStartServer(players: List<ServerPlayer>) {
        players.forEach { it.moveTo(pos.x, pos.y, pos.z) }
        super.onStartServer(players)
    }

    override fun reset() {
        startTime = TickHandler.currentTicks
    }

    override fun onStartClient() {
        Minecraft.getInstance().player?.moveTo(pos)
        MinecraftForge.EVENT_BUS.register(this)
    }

    @SubscribeEvent
    fun updateCamera(event: ViewportEvent.ComputeCameraAngles) {
        val partialTick = Minecraft.getInstance().partialTick
        val time = TickHandler.currentTicks - startTime + partialTick
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

    override val isEnd get() = TickHandler.currentTicks - startTime >= maxTime
}