package ru.hollowhorizon.hollowengine.common.registry.extend

import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation

@Suppress("UNCHECKED_CAST")
interface HollowDynamicRegistry {
    fun `hollow$clearDynamic`()

    fun clearDynamic() = `hollow$clearDynamic`()

    fun `hollow$getKey`(id: ResourceLocation): ResourceKey<*>?

    fun <T> getKey(id: ResourceLocation): ResourceKey<T>? = `hollow$getKey`(id) as ResourceKey<T>?

    fun `hollow$isPresent`(id: ResourceLocation): Boolean

    fun isPresent(id: ResourceLocation): Boolean = `hollow$isPresent`(id)
}
