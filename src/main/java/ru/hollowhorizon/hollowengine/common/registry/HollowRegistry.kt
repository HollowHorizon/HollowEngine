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

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import net.minecraft.core.Registry
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.CreativeModeTab
import net.minecraft.world.item.Items
import ru.hollowhorizon.hollowengine.HollowCore.MODID
import ru.hollowhorizon.hollowengine.common.utils.HollowCreativeTab
import ru.hollowhorizon.hollowengine.common.utils.mcTranslate
import ru.hollowhorizon.hollowengine.common.utils.rl
import kotlin.properties.ReadOnlyProperty

open class HollowRegistry(val modId: String = MODID) {
    /**
     * Avoid fake NotNulls parameters like BlockEntityType.Builder::build
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> promise(): T = null as T

    inline fun <reified T : Any> register(
        location: ResourceLocation,
        autoModel: AutoModelType? = AutoModelType.DEFAULT,
        registry: Registry<in T>? = null,
        noinline registryEntry: (ResourceLocation) -> T,
    ): IRegistryHolder<T> {
        REGISTRIES.entries.firstOrNull { it.key.isAssignableFrom(T::class.java) }?.let {
            val coreRegistry = it.value as CoreRegistry<T>
            val data by lazy { registryEntry(location) }
            coreRegistry[location] = data
            return IRegistryHolder { _, _ -> data }
        }

        return createRegistry(
            location,
            registry,
            autoModel,
            { registryEntry(location) },
            T::class.java
        ) as IRegistryHolder<T>
    }

    inline fun <reified T : Any> register(
        id: String,
        autoModel: AutoModelType? = AutoModelType.DEFAULT,
        registry: Registry<in T>? = null,
        noinline registryEntry: (ResourceLocation) -> T,
    ): IRegistryHolder<T> = register("$modId:$id".rl, autoModel, registry, registryEntry)

    fun creativeTab(name: String, block: CreativeModeTab.Builder.() -> Unit = {}) = register(name) {
        HollowCreativeTab.builder()
            .icon { Items.DIRT.defaultInstance }
            .title("itemGroup.$name".mcTranslate)
            .apply { block() }
            .build()
    }
}

open class CoreRegistry<T>(val registryName: ResourceLocation) {
    private val _entries: MutableMap<ResourceLocation, T> = Object2ObjectOpenHashMap()
    val entries: Map<ResourceLocation, T> get() = _entries.toMap()

    operator fun set(key: ResourceLocation, value: T) {
        _entries[key] = value
    }

    operator fun get(id: ResourceLocation): T =
        _entries[id] ?: throw IllegalStateException("Element $id not found in registry $registryName")

    operator fun get(value: T): ResourceLocation = _entries.entries.first { it.value == value }.key

    operator fun contains(id: ResourceLocation): Boolean = _entries.keys.any { it == id }
}

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class Registry

val REGISTRIES = Object2ObjectOpenHashMap<Class<*>, CoreRegistry<*>>()

lateinit var createRegistry: (ResourceLocation, Registry<*>?, AutoModelType?, () -> Any, Class<*>) -> IRegistryHolder<*>

typealias IRegistryHolder<T> = ReadOnlyProperty<Any?, T>