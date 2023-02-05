package ru.hollowhorizon.hollowstory.cutscenes.custom

import kotlinx.serialization.Serializable

@Serializable
class SceneScript {
    private val scriptFrames = mutableListOf<Int>()

    fun maxIndex(): Int {
        return scriptFrames.maxOrNull() ?: 0
    }
}