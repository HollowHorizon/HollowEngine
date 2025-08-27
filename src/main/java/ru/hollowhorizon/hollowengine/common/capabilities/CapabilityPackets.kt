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

@file:Suppress("UNCHECKED_CAST")

package ru.hollowhorizon.hollowengine.common.capabilities

import kotlinx.serialization.Serializable
import net.minecraft.client.Minecraft
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.Tag
import net.minecraft.world.entity.player.Player
import ru.hollowhorizon.hollowengine.HollowCore
import ru.hollowhorizon.hollowengine.api.ICapabilityDispatcher
import ru.hollowhorizon.hollowengine.common.handlers.HollowEventHandler
import ru.hollowhorizon.hollowengine.common.network.HollowPacket
import ru.hollowhorizon.hollowengine.common.network.HollowPacketHandler
import ru.hollowhorizon.hollowengine.common.network.sendAllInDimension
import ru.hollowhorizon.hollowengine.common.utils.nbt.ForTag
import ru.hollowhorizon.hollowengine.common.utils.rl

@HollowPacketHandler(HollowPacketHandler.Direction.TO_CLIENT)
@Serializable
class CSyncEntityCapabilityPacket(
    private val entityId: Int,
    val capability: String,
    val value: @Serializable(ForTag::class) Tag,
) : HollowPacket {
    override fun handle(player: Player) {
        val entity = player.level().getEntity(entityId)
        if (entity == null) {
            HollowEventHandler.ENTITY_TAGS.computeIfAbsent(entityId) { mutableMapOf() }[capability] = value
            return
        }
        val cap = (entity as ICapabilityDispatcher).capabilities[capability]

        if ((value as? CompoundTag)?.isEmpty == false) {
            cap?.deserializeNBT(value)
        }
    }
}

@HollowPacketHandler(HollowPacketHandler.Direction.TO_SERVER)
@Serializable
class SSyncEntityCapabilityPacket(
    private val entityId: Int,
    val capability: String,
    val value: @Serializable(ForTag::class) Tag,
) : HollowPacket {
    override fun handle(player: Player) {
        val entity = player.level().getEntity(entityId)
            ?: throw IllegalStateException("Entity with id $entityId not found: $this".apply(HollowCore.LOGGER::warn))
        val cap =
            (entity as ICapabilityDispatcher).capabilities[capability] ?: error("Capability not found: $capability")

        if (cap.canAcceptFromClient(player, value)) {
            cap.deserializeNBT(value)
            CSyncEntityCapabilityPacket(
                entityId, capability, value
            ).send(*player.level().server!!.playerList.players.toTypedArray())
        }
    }

}

@HollowPacketHandler(HollowPacketHandler.Direction.TO_CLIENT)
@Serializable
class CSyncLevelCapabilityPacket(
    val capability: String,
    val value: @Serializable(ForTag::class) Tag,
) : HollowPacket {
    override fun handle(player: Player) {
        val level = player.level() as ICapabilityDispatcher
        val cap = level.capabilities[capability]

        if ((value as? CompoundTag)?.isEmpty == false) {
            cap?.deserializeNBT(value)
        }
    }
}

@HollowPacketHandler(HollowPacketHandler.Direction.TO_SERVER)
@Serializable
class SSyncLevelCapabilityPacket(
    val level: String,
    val capability: String,
    val value: @Serializable(ForTag::class) Tag,
) : HollowPacket {
    override fun handle(player: Player) {
        val server = player.server ?: throw IllegalStateException("Server not found".apply(HollowCore.LOGGER::warn))
        val levelKey = server.levelKeys().find { it.location() == level.rl }
            ?: throw IllegalStateException("Unknown level: $level".apply(HollowCore.LOGGER::warn))
        val level = server.getLevel(levelKey)
            ?: throw IllegalStateException("Level not found: $level".apply(HollowCore.LOGGER::warn))
        val cap =
            (level as ICapabilityDispatcher).capabilities[capability] ?: error("Capability not found: $capability")

        if (cap.canAcceptFromClient(player, value)) {
            cap.deserializeNBT(value)
            CSyncLevelCapabilityPacket(capability, value).sendAllInDimension(level)
        }
    }

}

@HollowPacketHandler(HollowPacketHandler.Direction.TO_CLIENT)
@Serializable
class CSyncServerCapabilityPacket(
    val capability: String,
    val value: @Serializable(ForTag::class) Tag,
) : HollowPacket {
    override fun handle(player: Player) {
        Minecraft.getInstance().singleplayerServer?.let { server ->
            (server as ICapabilityDispatcher).capabilities[capability]
                ?.deserializeNBT(value)
        }
    }

}