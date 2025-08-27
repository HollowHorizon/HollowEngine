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
package ru.hollowhorizon.hollowengine.common.handlers

import net.minecraft.client.gui.screens.Screen
import net.minecraft.locale.Language
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.Tag
import net.minecraft.server.level.ServerLevel
import ru.hollowhorizon.hollowengine.HollowCore
import ru.hollowhorizon.hollowengine.api.ICapabilityDispatcher
import ru.hollowhorizon.hollowengine.api.deserializeCapabilities
import ru.hollowhorizon.hollowengine.api.serializeCapabilities
import ru.hollowhorizon.hollowengine.common.utils.mcTranslate
import ru.hollowhorizon.hollowengine.common.utils.nbt.loadAsNBT
import ru.hollowhorizon.hollowengine.common.utils.nbt.save
import ru.hollowhorizon.hollowengine.common.capabilities.CapabilityInstance
import ru.hollowhorizon.hollowengine.common.events.SubscribeEvent
import ru.hollowhorizon.hollowengine.common.events.client.ItemTooltipEvent
import ru.hollowhorizon.hollowengine.common.events.entity.EntityTrackingEvent
import ru.hollowhorizon.hollowengine.common.events.entity.player.PlayerEvent
import ru.hollowhorizon.hollowengine.common.events.level.LevelEvent
import ru.hollowhorizon.hollowengine.common.events.tick.TickEvent
import ru.hollowhorizon.hollowengine.mixins.DimensionDataStorageAccessor
import java.io.DataInputStream

object HollowEventHandler {
    val ENTITY_TAGS = hashMapOf<Int, MutableMap<String, Tag>>()

    @SubscribeEvent
    fun onTooltip(event: ItemTooltipEvent) {
        val desc = event.itemStack.item.descriptionId + ".hc_desc"
        val shiftDesc = event.itemStack.item.descriptionId + ".hc_shift_desc"
        val lang = Language.getInstance()

        if (lang.has(desc)) event.toolTip.add(desc.mcTranslate)

        if (Screen.hasShiftDown() && lang.has(shiftDesc)) event.toolTip.add(desc.mcTranslate)
    }

    @SubscribeEvent
    fun onStartTracking(event: EntityTrackingEvent) {
        (event.entity as ICapabilityDispatcher).capabilities.values.forEach(CapabilityInstance::synchronize)
    }

    @SubscribeEvent
    fun onPlayerClone(event: PlayerEvent.Clone) {
        if (event.wasDeath) {
            val oldCapabilities = (event.oldPlayer as ICapabilityDispatcher).capabilities
            val newCapabilities = (event.player as ICapabilityDispatcher).capabilities
            newCapabilities.clear()
            oldCapabilities.forEach { (key, value) ->
                newCapabilities[key] = value
            }
            event.player.capabilities.values.forEach(CapabilityInstance::synchronize)
        }
    }

    @SubscribeEvent
    fun onPlayerLoggedIn(event: PlayerEvent.Join) {
        val player = event.player as ICapabilityDispatcher

        player.capabilities.values.forEach(CapabilityInstance::synchronize)
    }

    @SubscribeEvent
    fun onLevelSave(event: LevelEvent.Save) {
        val level = event.level as ServerLevel
        val folder = (level.chunkSource.dataStorage as DimensionDataStorageAccessor).dataFolder

        val tag = CompoundTag()
        (level as ICapabilityDispatcher).serializeCapabilities(tag)
        val stream = folder.resolve("hc_capabilities.dat").outputStream()
        tag.save(stream)
        stream.close()
    }

    @SubscribeEvent
    fun onLevelLoad(event: LevelEvent.Load) {
        val level = event.level as ServerLevel
        val folder = (level.chunkSource.dataStorage as DimensionDataStorageAccessor).dataFolder
        val capabilities = folder.resolve("hc_capabilities.dat")
        if (capabilities.exists()) {
            try {
                val tag = DataInputStream(capabilities.inputStream()).loadAsNBT() as CompoundTag
                (level as ICapabilityDispatcher).deserializeCapabilities(tag)
            } catch (e: Exception) {
                HollowCore.LOGGER.warn(
                    "Exception, while loading capabilities for level {}: ",
                    level.dimension().location(),
                    e
                )
            }
        }
    }

    @SubscribeEvent
    fun onChangeDimension(event: PlayerEvent.ChangeDimension) {
        (event.to as ICapabilityDispatcher).capabilities.values.forEach(CapabilityInstance::synchronize)
    }

    @SubscribeEvent
    fun onEntityTick(event: TickEvent.Entity) {
        if (event.entity.level().isClientSide) {
            ENTITY_TAGS[event.entity.id]?.let { map ->
                val capabilities = (event.entity as ICapabilityDispatcher).capabilities
                map.forEach { (name, tag) ->
                    capabilities[name]?.deserializeNBT(tag)
                }
                ENTITY_TAGS.remove(event.entity.id)
            }
        }
    }
}
