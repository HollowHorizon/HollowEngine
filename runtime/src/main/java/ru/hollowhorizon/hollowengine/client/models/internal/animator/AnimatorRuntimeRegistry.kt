package ru.hollowhorizon.hollowengine.client.models.internal.animator

import java.util.UUID

data class AnimatorRuntimeKey(
    val snapshotId: UUID,
    val nodeId: UUID,
    val model: String,
)

object AnimatorRuntimeRegistry {
    private val runtimes = linkedMapOf<AnimatorRuntimeKey, AnimatorRuntime>()

    fun get(key: AnimatorRuntimeKey): AnimatorRuntime =
        runtimes.getOrPut(key, ::AnimatorRuntime)

    fun clear(key: AnimatorRuntimeKey) {
        runtimes.remove(key)
    }
}
