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

import com.mojang.math.Vector3f
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec3
import net.minecraftforge.client.event.ViewportEvent.ComputeCameraAngles
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.eventbus.api.SubscribeEvent
import ru.hollowhorizon.hc.api.utils.Polymorphic
import ru.hollowhorizon.hc.client.handlers.TickHandler
import ru.hollowhorizon.hc.client.utils.nbt.ForVec3
import ru.hollowhorizon.hc.client.utils.nbt.ForVector3f

@Serializable
@Polymorphic(ICameraPath::class)
class StaticCameraPath(
    override val maxTime: Int,
    val pos: @Serializable(ForVec3::class) Vec3,
    val rotation: @Serializable(ForVector3f::class) Vector3f,
) : ICameraPath {
    @Transient
    var startTime = TickHandler.currentTicks
    override val isEnd get() = TickHandler.currentTicks - startTime >= maxTime

    override fun reset() {
        startTime = TickHandler.currentTicks
    }

    override fun serverUpdate(players: List<Player>) {
        players.filterIsInstance<ServerPlayer>().forEach { it.teleportTo(it.getLevel(), pos.x, pos.y, pos.z, it.yHeadRot, it.xRot) }
    }

    override fun onStartClient() {
        super.onStartClient()
        MinecraftForge.EVENT_BUS.register(this)
    }

    @SubscribeEvent
    fun onCameraSetup(event: ComputeCameraAngles) {
        if (isEnd) MinecraftForge.EVENT_BUS.unregister(this)

        event.yaw = rotation.y()
        event.pitch = rotation.x()
        event.roll = rotation.z()
    }
}