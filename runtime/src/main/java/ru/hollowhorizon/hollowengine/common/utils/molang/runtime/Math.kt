package ru.hollowhorizon.hollowengine.common.utils.molang.runtime

import net.minecraft.util.Mth
import kotlin.math.absoluteValue
import kotlin.math.truncate
import kotlin.random.Random

object Math {
    @JvmField
    val pi = Mth.PI

    @JvmStatic
    fun cos(value: Float) = Mth.cos(value* Mth.DEG_TO_RAD)
    @JvmStatic
    fun sin(value: Float) = Mth.sin(value* Mth.DEG_TO_RAD)
    @JvmStatic
    fun floor(value: Float) = Mth.floor(value)
    @JvmStatic
    fun ceil(value: Float) = Mth.ceil(value)
    @JvmStatic
    fun round(value: Float) = kotlin.math.round(value)
    @JvmStatic
    fun trunc(value: Float) = truncate(value)
    @JvmStatic
    fun abs(value: Float) = value.absoluteValue
    @JvmStatic
    fun clamp(value: Float, min: Float, max: Float) = value.coerceIn(min, max)
    @JvmStatic
    fun random(low: Float, high: Float) = Random.nextFloat() * (high - low) + low
    @JvmStatic
    fun min(left: Float, right: Float) = kotlin.math.min(left, right)
    @JvmStatic
    fun max(left: Float, right: Float) = kotlin.math.max(left, right)
    @JvmStatic
    fun sqrt(value: Float) = kotlin.math.sqrt(value)
    @JvmStatic
    fun exp(value: Float) = kotlin.math.exp(value)
    @JvmStatic
    fun lerp(start: Float, end: Float, t: Float) = start + (end - start) * t
}