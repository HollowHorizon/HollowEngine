package ru.hollowhorizon.hollowengine.client.models.internal.animations.interpolations

import java.util.*

abstract class Interpolator<T>(val keys: FloatArray, val values: Array<T>) {
    abstract fun compute(time: Float): T

    val duration = keys.last()

    val Float.animIndex: Int
        get() {
            val index = Arrays.binarySearch(keys, this)

            return if (index >= 0) index
            else 0.coerceAtLeast(-index - 2)
        }
}