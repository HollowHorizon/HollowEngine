package ru.hollowhorizon.hollowengine.common.config

data class FloatRange(val minValue: Float, val maxValue: Float) {
    operator fun contains(number: Number): Boolean {
        return number.toFloat().let { it >= minValue && it <= maxValue }
    }
}