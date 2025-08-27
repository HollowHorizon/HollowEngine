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

package ru.hollowhorizon.hollowengine.api

import net.minecraft.nbt.CompoundTag
import ru.hollowhorizon.hollowengine.common.capabilities.CapabilityInstance
import ru.hollowhorizon.hollowengine.common.events.capabilities.LoadCapabilitiesEvent
import ru.hollowhorizon.hollowengine.common.events.post

/**
 * Adds the ability to use capability in classes (including children)
 *
 * Available by default in [net.minecraft.server.MinecraftServer],
 * [net.minecraft.world.entity.Entity], [net.minecraft.world.level.block.entity.BlockEntity]
 * and [net.minecraft.world.level.Level].
 *
 * If your class inherits one of the child classes [ICapabilityDispatcher], then you can use the capability system
 * without any problems.
 *
 * [capabilities] returns a list of the assigned [CapabilityInstance]
 */
interface ICapabilityDispatcher {
    val capabilities: MutableMap<String, CapabilityInstance>
}

/**
 * Serializes all capabilities that have been assigned to a given [ICapabilityDispatcher]
 * @param tag specifies where all data from capability should be saved
 */
fun ICapabilityDispatcher.serializeCapabilities(tag: CompoundTag) {
    val nbt = CompoundTag()
    capabilities.forEach {
        nbt.put(it.key, it.value.serializeNBT())
    }
    tag.put("hc_capabilities", nbt)
}

/**
 * Deserializes [ICapabilityDispatcher] by loading data for [CapabilityInstance] from NBT
 * @param tag where you need to upload all data from
 */
fun ICapabilityDispatcher.deserializeCapabilities(tag: CompoundTag) {
    val capabilities = tag.getCompound("hc_capabilities")
    for (key in capabilities.allKeys) {
        this.capabilities[key]?.deserializeNBT(capabilities.getCompound(key))
    }
}

fun ICapabilityDispatcher.syncIfNeeded() {
    capabilities.values.filter { it.isChanged }.forEach {
        it.synchronize()
        it.isChanged = false
    }
}

fun ICapabilityDispatcher.initialize() {
    LoadCapabilitiesEvent(this, capabilities).post()
}