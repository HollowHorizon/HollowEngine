package ru.hollowhorizon.hollowengine.common.utils.math

import java.util.*
import kotlin.math.PI
import kotlin.math.abs

const val FUZZY_EQ_F = 1e-5f
const val FUZZY_EQ_D = 1e-10
const val PI_F = PI.toFloat()

const val DEG_2_RAD = PI / 180.0
const val RAD_2_DEG = 180.0 / PI

inline fun Float.toDeg() = this * RAD_2_DEG.toFloat()
inline fun Float.toRad() = this * DEG_2_RAD.toFloat()
inline fun Double.toDeg() = this * RAD_2_DEG
inline fun Double.toRad() = this * DEG_2_RAD

inline fun isFuzzyEqual(a: Float, b: Float, eps: Float = FUZZY_EQ_F) = (a - b).isFuzzyZero(eps)
inline fun isFuzzyEqual(a: Double, b: Double, eps: Double = FUZZY_EQ_D) = (a - b).isFuzzyZero(eps)

inline fun Float.isFuzzyZero(eps: Float = FUZZY_EQ_F) = abs(this) <= eps
inline fun Double.isFuzzyZero(eps: Double = FUZZY_EQ_D) = abs(this) <= eps

inline fun Int.clamp(min: Int, max: Int): Int {
    require(max >= min) { "max ($max) is smaller than min ($min) " }
    return when {
        this < min -> min
        this > max -> max
        else -> this
    }
}

inline fun Float.clamp(min: Float = 0f, max: Float = 1f): Float {
    require(max >= min) { "max ($max) is smaller than min ($min) " }
    return when {
        this < min -> min
        this > max -> max
        else -> this
    }
}

inline fun Double.clamp(min: Double = 0.0, max: Double = 1.0): Double {
    require(max >= min) { "max ($max) is smaller than min ($min) " }
    return when {
        this < min -> min
        this > max -> max
        else -> this
    }
}

fun Double.toString(precision: Int): String = "%.${precision.clamp(0, 12)}f".format(Locale.ENGLISH, this)
fun Float.toString(precision: Int): String = toDouble().toString(precision)