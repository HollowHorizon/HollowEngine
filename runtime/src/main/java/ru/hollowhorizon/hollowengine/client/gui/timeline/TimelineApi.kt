package ru.hollowhorizon.hollowengine.client.gui.timeline

import de.fabmax.kool.modules.ui2.UiScope
import de.fabmax.kool.modules.ui2.mutableStateOf
import de.fabmax.kool.util.Color

interface PropertyDriver<T> {
    fun interpolate(start: T, end: T, fraction: Float): T

    fun apply(value: T)

    fun UiScope.drawEditor(value: T, onChange: (T) -> Unit)
}

abstract class BaseAnimTrack(name: String, val color: Color = Color.WHITE) {
    val nameState = mutableStateOf(name)
    val isLocked = mutableStateOf(false)
    val isVisible = mutableStateOf(true)

    abstract fun update(time: Float)
    abstract fun getKeysAsList(): MutableList<out Keyframe<*>>
}