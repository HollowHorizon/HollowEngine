/*
 * MIT License
 *
 * Copyright (c) 2024 HollowHorizon
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package ru.hollowhorizon.hollowengine.client.utils.math

import de.fabmax.kool.math.Vec3f
import net.minecraft.util.Mth
import kotlin.math.pow

const val EL = (2 * Mth.PI) / 3
const val EL2 = (2 * Mth.PI) / 4.5f
const val n1 = 7.5625f
const val d1 = 2.75f

enum class Interpolation(private val function: (Float) -> Float) {
    LINEAR({ it }),
    SINE_IN({ 1.0f - Mth.cos(it * Mth.PI / 2) }),
    SINE_OUT({ Mth.sin(it * Mth.PI / 2) }),
    SINE_IN_OUT({ -(Mth.cos(Mth.PI * it) - 1f) / 2f }),
    QUAD_IN({ it * it }),
    QUAD_OUT({ 1f - (1 - it) * (1 - it) }),
    QUAD_IN_OUT({ if (it < 0.5f) 2 * it * it else 1 - (-2 * it + 2) * (-2 * it + 2) / 2 }),
    CUBIC_IN({ it * it * it }),
    CUBIC_OUT({ 1f - (1 - it) * (1 - it) * (1 - it) }),
    CUBIC_IN_OUT({ if (it < 0.5f) 4 * it * it * it else 1 - (-2 * it + 2) * (-2 * it + 2) * (-2 * it + 2) / 2 }),
    QUART_IN({ it.pow(4) }),
    QUART_OUT({ 1f - (1 - it) * (1 - it) * (1 - it) * (1 - it) }),
    QUART_IN_OUT({ if (it < 0.5f) it.pow(4) * 8 else 1 - (-2 * it + 2).pow(4) / 2 }),
    QUINT_IN({ it.pow(5) }),
    QUINT_OUT({ 1f - (1 - it) * (1 - it) * (1 - it) * (1 - it) * (1 - it) }),
    QUINT_IN_OUT({ if (it < 0.5f) it.pow(5) * 16 else 1 - (-2 * it + 2).pow(5) / 2 }),
    EXPO_IN({ if (it == 0f) 0f else 2f.pow(10 * it - 10) }),
    EXPO_OUT({ if (it == 1f) 1f else 1f - 2f.pow(-10 * it) }),
    EXPO_IN_OUT({
        if (it == 0f) 0f else if (it == 1f) 1f else if (it < 0.5f) 2f.pow(20 * it - 10) / 2 else (2f - 2f.pow(
            -20 * it + 10
        )) / 2
    }),
    CIRC_IN({ 1f - Mth.sqrt(1 - it * it) }),
    CIRC_OUT({ Mth.sqrt(1 - (it - 1).pow(2)) }),
    CIRC_IN_OUT({ if (it < 0.5f) (1 - Mth.sqrt(1 - (2 * it).pow(2))) / 2 else (Mth.sqrt(1 - (-2 * it + 2).pow(2)) + 1) / 2 }),
    BACK_IN({ 2.70158f * it.pow(3) - 1.70158f * it.pow(2) }),
    BACK_OUT({ 1 + 2.70158f * (it - 1).pow(3) + 1.70158f * (it - 1).pow(2) }),
    BACK_IN_OUT({ if (it < 0.5f) ((2 * it).pow(2) * (8.45316f * it - 3.22658f)) / 2 else ((2 * it - 2).pow(2) * (4.22658f * (it * 2 - 2) + 3.22658f) + 2) / 2 }),
    ELASTIC_IN({ if (it == 0f) 0f else if (it == 1f) 1f else -(2f.pow(10 * it - 10)) * Mth.sin((it * 10 - 10.75f) * EL) }),
    ELASTIC_OUT({ if (it == 0f) 0f else if (it == 1f) 1f else 2f.pow(-10 * it) * Mth.sin((it * 10 - 0.75f) * EL) + 1 }),
    ELASTIC_IN_OUT({
        when {
            it == 0f -> 0f
            it == 1f -> 1f
            it < 0.5f -> -(2f.pow(20 * it - 10) * Mth.sin((20 * it - 11.125f) * EL2)) / 2f
            else -> (2f.pow(-20 * it + 10) * Mth.sin((20 * it - 11.125f) * EL2)) / 2f + 1
        }
    }),
    BOUNCE_IN({ bounceIn(it) }),
    BOUNCE_OUT({ bounceOut(it) }),
    BOUNCE_IN_OUT({ bounceInOut(it) }),
    STEP({ if (it > 0.5) 1f else 0f }),
    CATMULLROM({ 1f }) // TODO: Надо придумать, как её использовать вместе с остальными, поскольку для неё больше данных нужно
    ;

    operator fun invoke(float: Float) = function(float)

    companion object {
        fun catmullRom(
            t: Float,
            a: Vec3f,
            b: Vec3f,
            c: Vec3f,
            d: Vec3f,
        ): Vec3f {
            return Vec3f(
                catmullRom(t, a.x, b.x, c.x, d.x),
                catmullRom(t, a.y, b.y, c.y, d.y),
                catmullRom(t, a.z, b.z, c.z, d.z),
            )
        }

        fun catmullRom(
            t: Float,
            a: Float,
            b: Float,
            c: Float,
            d: Float,
        ): Float {
            val v0 = -0.5f * a + 1.5f * b - 1.5f * c + 0.5f * d
            val v1 = a - 2.5f * b + 2 * c - 0.5f * d
            val v2 = -0.5f * a + 0.5f * c
            val tt = t * t
            return v0 * t * tt + v1 * tt + v2 * t + b
        }
    }
}

private fun bounceIn(x: Float): Float {
    val adjustedX = 1 - x
    return when {
        adjustedX < 1 / d1 -> 1 - n1 * adjustedX * adjustedX
        adjustedX < 2 / d1 -> 1 - n1 * (adjustedX - 1.5f / d1) * (adjustedX - 1.5f) + 0.75f
        adjustedX < 2.5 / d1 -> 1 - n1 * (adjustedX - 2.25f / d1) * (adjustedX - 2.25f) + 0.9375f
        else -> 1 - n1 * (adjustedX - 2.625f / d1) * (adjustedX - 2.625f) + 0.984375f
    }
}

private fun bounceOut(x: Float): Float {
    return when {
        x < 1 / d1 -> n1 * x * x
        x < 2 / d1 -> n1 * (x - 1.5f / d1) * (x - 1.5f) + 0.75f
        x < 2.5 / d1 -> n1 * (x - 2.25f / d1) * (x - 2.25f) + 0.9375f
        else -> n1 * (x - 2.625f / d1) * (x - 2.625f) + 0.984375f
    }
}

private fun bounceInOut(x: Float): Float {
    return if (x < 0.5f) {
        val adjustedX = 1 - x * 2
        when {
            adjustedX < 1 / d1 -> (1 - n1 * adjustedX * adjustedX) / 2
            adjustedX < 2 / d1 -> (1 - n1 * (adjustedX - 1.5f / d1) * (adjustedX - 1.5f) + 0.75f) / 2
            adjustedX < 2.5 / d1 -> (1 - n1 * (adjustedX - 2.25f / d1) * (adjustedX - 2.25f) + 0.9375f) / 2
            else -> (1 - n1 * (adjustedX - 2.625f / d1) * (adjustedX - 2.625f) + 0.984375f) / 2
        }
    } else {
        val adjustedX = x * 2 - 1
        when {
            adjustedX < 1 / d1 -> (1 + n1 * adjustedX * adjustedX) / 2
            adjustedX < 2 / d1 -> (1 + n1 * (adjustedX - 1.5f / d1) * (adjustedX - 1.5f) + 0.75f) / 2
            adjustedX < 2.5 / d1 -> (1 + n1 * (adjustedX - 2.25f / d1) * (adjustedX - 2.25f) + 0.9375f) / 2
            else -> (1 + n1 * (adjustedX - 2.625f / d1) * (adjustedX - 2.625f) + 0.984375f) / 2
        }
    }
}