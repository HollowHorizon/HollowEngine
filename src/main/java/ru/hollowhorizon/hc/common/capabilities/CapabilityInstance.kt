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

package ru.hollowhorizon.hc.common.capabilities

import net.minecraft.client.Minecraft
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.Tag
import net.minecraft.server.dedicated.DedicatedServer
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity
import ru.hollowhorizon.hc.api.ICapabilityDispatcher
import ru.hollowhorizon.hc.common.capabilities.containers.HollowContainer
import ru.hollowhorizon.hc.common.network.sendAllInDimension
import ru.hollowhorizon.hc.common.network.sendTrackingEntityAndSelf

/**
 * A base class for HollowCore's implementation of the Capability system.
 * Provides functionality for creating, serializing, and synchronizing data.
 *
 * Example of usage you can find [here](https://0mods.team/docs/hollowcore/capabilities/)
 *
 * @property provider The object that provides this Capability.
 * @property isOneSided Flag indicating one-way capability (server or client only).
 * @property properties A list of Capability properties to be serialized and synchronized.
 * @property notUsedTags NBT tags not used in properties, but saved for compatibility.
 * @property containers List the HollowContainers associated with this Capability.
 * @property isChanged Flag to indicate that the Capability should be synchronized after the changes.
 */
@Suppress("API_STATUS_INTERNAL")
open class CapabilityInstance {
    lateinit var provider: ICapabilityDispatcher
    var isOneSided = false
    val properties = ArrayList<CapabilityProperty<CapabilityInstance, *>>()
    var notUsedTags = CompoundTag()
    val containers = ArrayList<HollowContainer>()
    var isChanged = true // For Capability to sync after start

    /**
     * Creates a synchronized property with a specified default value.
     *
     * @param default The default value for the property.
     * @return Instance of [CapabilityProperty] with a specified default value.
     */
    fun <T> syncable(default: T, transferFrom: (T, T?) -> Unit = { o, n -> }) =
        CapabilityProperty<CapabilityInstance, T>(default, transferFrom).apply {
            properties += this
        }

    /**
     * Synchronizes the current state of the Capability with the client or server, depending on the execution side.
     * If the Capability is one-way, synchronization is not performed.
     */
    fun synchronize() {
        if (isOneSided) return

        val tag = serializeNBT()

        when (val target = provider) {
            is Entity -> {
                if (target.level().isClientSide) {
                    if (canAcceptFromClient(Minecraft.getInstance().player!!, tag)) SSyncEntityCapabilityPacket(
                        target.id,
                        javaClass.name,
                        tag
                    ).send()
                } else CSyncEntityCapabilityPacket(target.id, javaClass.name, tag).sendTrackingEntityAndSelf(target)
            }

            is Level -> {
                if (target.isClientSide) {
                    if (canAcceptFromClient(Minecraft.getInstance().player!!, tag)) SSyncLevelCapabilityPacket(
                        target.dimension().location().toString(),
                        javaClass.name, tag
                    ).send()
                } else CSyncLevelCapabilityPacket(javaClass.name, tag).sendAllInDimension(target)
            }

            is BlockEntity -> target.setChanged()

            is DedicatedServer -> {
                CSyncServerCapabilityPacket(javaClass.name, tag).send(*target.playerList.players.toTypedArray())
            }
        }
    }

    /**
     * Serializes the current state of the Capability into a [CompoundTag] object.
     *
     * @return [CompoundTag] containing serialized Capability data.
     */
    open fun serializeNBT() = notUsedTags.copy().apply {
        properties.forEach { it.serialize(this) }
    }

    /**
     * Deserializes the Capability state from the [Tag] object.
     *
     * @param nbt [Tag] containing data to restore the Capability state.
     */
    open fun deserializeNBT(nbt: Tag) {
        val tag = nbt as? CompoundTag ?: return
        notUsedTags.merge(tag)
        properties.forEach { if (it.deserialize(tag)) nbt.remove(it.defaultName) }
    }

    /**
     * Determines whether the Capability can accept data from the client.
     * Defaults to 'false' to prevent potential vulnerabilities.
     * Override this method to implement access rights verification or data validation.
     *
     * @param player A player who sends data from the client.
     * @param tag [Tag] containing data sent from the client.
     * @return 'true' if the data can be accepted; 'false' otherwise.
     */
    open fun canAcceptFromClient(player: Player, tag: Tag): Boolean {
        return false
    }

    /**
     * Creates a synchronized list with specified items.
     * Changes in the list are automatically synchronized between the server and the client.
     *
     * @param list Original list (empty by default).
     * @param [T] The type of items in the list.
     * @return Synchronized list.
     */
    inline fun <reified T : Any> syncableList(list: MutableList<T> = ArrayList()) =
        syncable(SyncableListImpl(list, T::class.java) { isChanged = true })

    /**
     * Creates a synchronized list with specified items.
     * Changes in the list are automatically synchronized between the server and the client.
     *
     * @param elements List items.
     * @param [T] Item type.
     * @return Synchronized list.
     */
    inline fun <reified T : Any> syncableList(vararg elements: T) = syncableList(elements.toMutableList())

    /**
     * Creates a synchronized map with the specified key and value types.
     * Changes in the map are automatically synchronized between the server and the client.
     *
     * @param [K] Key type.
     * @param [V] Value type.
     * @return Synchronized map.
     */
    inline fun <reified K : Any, reified V : Any> syncableMap() =
        syncable(SyncableMapImpl(HashMap(), K::class.java, V::class.java) { isChanged = true })
}