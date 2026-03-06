package ru.hollowhorizon.hollowengine.client.models.internal.manager

import net.minecraft.resources.ResourceLocation

data class PreparedModelUpdate<T>(
    val exists: Boolean,
    val loaded: Result<T>? = null,
)

internal data class ModelSwap<T>(
    val next: T,
    val retired: T? = null,
)

internal object ModelReloadCoordinator {
    fun reloadTargets(
        cached: Set<ResourceLocation>,
        indexed: Set<ResourceLocation>,
    ): Set<ResourceLocation> = linkedSetOf<ResourceLocation>().apply {
        addAll(cached)
        addAll(indexed)
    }

    fun <T> resolveSwap(
        current: T,
        prepared: PreparedModelUpdate<T>,
        empty: T,
    ): ModelSwap<T> {
        if (!prepared.exists) {
            return if (current === empty) ModelSwap(empty) else ModelSwap(empty, current)
        }

        val loaded = prepared.loaded?.getOrNull()
        if (loaded != null) {
            return if (current === loaded) ModelSwap(loaded) else {
                val retired = current.takeUnless { it === empty || it === loaded }
                ModelSwap(loaded, retired)
            }
        }

        return ModelSwap(current)
    }
}
