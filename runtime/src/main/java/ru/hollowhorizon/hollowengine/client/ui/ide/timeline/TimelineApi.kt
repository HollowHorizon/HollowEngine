package ru.hollowhorizon.hollowengine.client.ui.ide.timeline

import de.fabmax.kool.modules.ui2.mutableStateOf
import de.fabmax.kool.util.Color

interface PropertyDriver<T> {
    fun interpolate(start: T, end: T, fraction: Float): T

    fun apply(value: T)
}

abstract class BaseAnimTrack(name: String, val color: Color = Color.WHITE) {
    val nameState = mutableStateOf(name)
    val isLocked = mutableStateOf(false)
    val isVisible = mutableStateOf(true)

    abstract fun update(time: Float)
    abstract fun getKeysAsList(): MutableList<out Keyframe<*>>
}