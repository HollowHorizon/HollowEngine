package ru.hollowhorizon.hollowengine.cutscenes.world

import kotlinx.serialization.Serializable

@Serializable
class SceneWorld {
    private val worldChanges = mutableMapOf<Int, WorldChange>()

    fun maxIndex(): Int {
        return worldChanges.keys.maxOrNull() ?: 0
    }
}