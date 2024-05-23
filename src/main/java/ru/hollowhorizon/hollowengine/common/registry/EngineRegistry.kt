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

package ru.hollowhorizon.hollowengine.common.registry

import com.google.common.collect.ImmutableMap
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.server.packs.resources.SimplePreparableReloadListener
import net.minecraft.util.profiling.ProfilerFiller
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.event.AddReloadListenerEvent
import net.minecraftforge.eventbus.api.GenericEvent
import kotlin.reflect.KClass

abstract class EngineRegistry<T : RegistryEntry> {
    val VALUES = HashMap<ResourceLocation, Entry>()

    fun entries() = ImmutableMap.copyOf(VALUES)

    @Suppress("UNCHECKED_CAST")
    fun <V : T> find(path: ResourceLocation): V =
        (VALUES[path] ?: throw IllegalStateException("Object $path not found!")).entry.invoke() as V

    inline fun <reified V : T> find(): V = VALUES.values.find { it.type == V::class }?.entry?.invoke() as? V
        ?: throw IllegalStateException("Object ${V::class} not found!")

    abstract fun init()

    inline fun <reified V : T> register(location: ResourceLocation, crossinline value: () -> V) {
        VALUES[location] = Entry(V::class) { value().apply { type = location } }
    }

    fun reload() {
        VALUES.clear()
        init()
        MinecraftForge.EVENT_BUS.post(RegisterEntryEvent(this))
    }

    fun onReload(event: AddReloadListenerEvent) {
        event.addListener(object : SimplePreparableReloadListener<Unit>() {
            override fun prepare(pResourceManager: ResourceManager, pProfiler: ProfilerFiller) {}

            override fun apply(pObject: Unit, pResourceManager: ResourceManager, pProfiler: ProfilerFiller) {
                reload()
            }
        })
    }

    inner class Entry(val type: KClass<out T>, val entry: () -> T)
}

class RegisterEntryEvent<T : RegistryEntry>(val registry: EngineRegistry<T>) : GenericEvent<T>() {
    inline fun <reified V : T> register(location: ResourceLocation, crossinline node: () -> V) {
        registry.register(location, node)
    }
}

interface RegistryEntry {
    var type: ResourceLocation
}