package ru.hollowhorizon.hollowengine.client.ui.ide.timeline

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ru.hollowhorizon.hollowengine.common.utils.Color


interface PropertyDriver<T> {
    fun interpolate(start: T, end: T, fraction: Float): T

    fun apply(value: T)
}

abstract class BaseAnimTrack(name: String, val color: Color = Color.WHITE) {
    val nameState = mutableStateOf(name)
    var isLocked by mutableStateOf(false)
    var isVisible by mutableStateOf(true)

    abstract fun update(time: Float)
    abstract fun getKeysAsList(): MutableList<out Keyframe<*>>
}