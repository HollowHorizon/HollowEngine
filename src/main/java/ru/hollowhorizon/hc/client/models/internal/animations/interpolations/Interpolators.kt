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

package ru.hollowhorizon.hc.client.models.internal.animations.interpolations

import de.fabmax.kool.math.MutableQuatF
import de.fabmax.kool.math.QuatF
import de.fabmax.kool.math.Vec3f
import de.fabmax.kool.math.Vec4f
import ru.hollowhorizon.hc.client.utils.math.Interpolation
import ru.hollowhorizon.hc.client.utils.math.catmullRom
import ru.hollowhorizon.hc.client.utils.math.conjugate
import ru.hollowhorizon.hc.common.utils.molang.runtime.Math.cos
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sin
import kotlin.math.sqrt

class Vec3Step(keys: FloatArray, values: Array<Vec3f>) : Interpolator<Vec3f>(keys, values) {
    override fun compute(time: Float): Vec3f = values[time.animIndex]
}

class QuatStep(keys: FloatArray, values: Array<QuatF>) : Interpolator<QuatF>(keys, values) {
    override fun compute(time: Float): QuatF = values[time.animIndex]
}

class LinearSingle(keys: FloatArray, values: Array<FloatArray>) : Interpolator<FloatArray>(keys, values) {
    override fun compute(time: Float): FloatArray {
        if (time <= keys.first() || keys.size == 1) return values.first()
        else if (time >= keys.last()) return values.last()
        else {
            val previousIndex = time.animIndex
            val nextIndex = previousIndex + 1
            val local = time - keys[previousIndex]
            val delta = keys[nextIndex] - keys[previousIndex]
            val alpha = local / delta
            val previousValue = values[previousIndex]
            val nextValue = values[nextIndex]
            val interpolatedValue =
                FloatArray(previousValue.size) { i -> previousValue[i] + (nextValue[i] - previousValue[i]) * alpha }
            return interpolatedValue
        }
    }

}

class Linear(keys: FloatArray, values: Array<Vec3f>) : Interpolator<Vec3f>(keys, values) {
    override fun compute(time: Float): Vec3f {
        if (time <= keys.first() || keys.size == 1) return values.first()
        else if (time >= keys.last()) return values.last()
        else {
            val previousIndex = time.animIndex
            val nextIndex = previousIndex + 1
            val local = time - keys[previousIndex]
            val delta = keys[nextIndex] - keys[previousIndex]
            val alpha = local / delta
            val previousPoint = Vec3f(values[previousIndex])
            val nextPoint = values[nextIndex]
            return previousPoint.mix(nextPoint, alpha)
        }
    }
}

class SphericalLinear(keys: FloatArray, values: Array<QuatF>) : Interpolator<QuatF>(keys, values) {
    override fun compute(time: Float): QuatF {
        if (time <= keys.first() || keys.size == 1) return values.first()
        else if (time >= keys.last()) return values.last()
        else {
            val previousIndex = time.animIndex
            val nextIndex = previousIndex + 1

            val local = time - keys[previousIndex]
            val delta = keys[nextIndex] - keys[previousIndex]
            val alpha = local / delta

            val prev = values[previousIndex]
            val next = values[nextIndex]
            val previousPoint = QuatF(prev.x, prev.y, prev.z, prev.w)
            val nextPoint = QuatF(next.x, next.y, next.z, next.w)

            val r = previousPoint.mix(nextPoint, alpha)
            return QuatF(r.x, r.y, r.z, r.w)
        }
    }
}

class Catmullrom(keys: FloatArray, values: Array<Vec3f>): Interpolator<Vec3f>(keys, values) {
    override fun compute(time: Float): Vec3f {
        if (time <= keys.first()) return values.first()
        if (time >= keys.last()) return values.last()

        val i = time.animIndex

        // Индексы контрольных точек: p0, p1, p2, p3
        val i0 = (i - 1).coerceAtLeast(0)
        val i1 = i
        val i2 = (i + 1).coerceAtMost(values.lastIndex)
        val i3 = (i + 2).coerceAtMost(values.lastIndex)

        val t0 = keys[i1]
        val t1 = keys[i2]
        val localT = ((time - t0) / (t1 - t0)).coerceIn(0f, 1f)

        return Interpolation.catmullRom(
            localT,
            values[i0], values[i1],
            values[i2], values[i3]
        )
    }
}

class CatmullromQuat(
    keys: FloatArray,
    values: Array<QuatF>
) : Interpolator<QuatF>(keys, values) {
    override fun compute(time: Float): QuatF {
        if (time <= keys.first()) return values.first()
        if (time >= keys.last()) return values.last()

        val i = time.animIndex

        val i0 = (i - 1).coerceAtLeast(0)
        val i1 = i
        val i2 = (i + 1).coerceAtMost(values.lastIndex)
        val i3 = (i + 2).coerceAtMost(values.lastIndex)

        val t0 = keys[i1]
        val t1 = keys[i2]
        val localT = ((time - t0) / (t1 - t0)).coerceIn(0f, 1f)

        val q0 = values[i0]
        val q1 = values[i1]
        val q2 = values[i2]
        val q3 = values[i3]

        return squad(q1, q2, intermediate(q0, q1, q2), intermediate(q1, q2, q3), localT)
    }

    private fun intermediate(q0: QuatF, q1: QuatF, q2: QuatF): QuatF {
        val invQ1 = q1.conjugate()
        val log1 = log(invQ1 * q0)
        val log2 = log(invQ1 * q2)
        val avg = ((log1.add(log2, MutableQuatF())) * -0.25f).exp()
        return q1 * avg
    }

    private fun QuatF.exp(): QuatF {
        val v = Vec3f(this.x, this.y, this.z)
        val len = v.length()
        val sinLen = sin(len)
        val cosLen = cos(len)
        val scale = if (len > 1e-6f) sinLen / len else 1f
        return QuatF(cosLen, v.x * scale, v.y * scale, v.z * scale)
    }

    private fun log(q: QuatF): QuatF {
        // q = [w, (x, y, z)]
        val v = Vec3f(q.x, q.y, q.z)
        val len = v.length()
        val angle = atan2(len, q.w)
        val scale = if (len > 1e-6f) angle / len else 0f
        return QuatF(0f, v.x * scale, v.y * scale, v.z * scale)
    }

    private fun squad(q1: QuatF, q2: QuatF, s1: QuatF, s2: QuatF, t: Float): QuatF {
        val slerp1 = q1.mix(q2, t)
        val slerp2 = s1.mix(s2, t)
        return slerp1.mix(slerp2, 2f * t * (1f - t))
    }
}

fun MutableQuatF.sphericalLerp(target: QuatF, alpha: Float) {
    val cosom = Math.fma(x, target.x, Math.fma(y, target.y, Math.fma(z, target.z, w * target.w)))
    val absCosom = abs(cosom)
    val scale0: Float
    var scale1: Float
    if (1.0f - absCosom > 1E-6f) {
        val sinSqr = 1.0f - absCosom * absCosom
        val sinom = 1.0f / sqrt(sinSqr)
        val omega = atan2(sinSqr * sinom, absCosom)
        scale0 = (sin((1.0f - alpha) * omega) * sinom)
        scale1 = (sin(alpha * omega) * sinom)
    } else {
        scale0 = 1.0f - alpha
        scale1 = alpha
    }
    scale1 = if (cosom >= 0.0f) scale1 else -scale1
    set(
        Math.fma(scale0, x, scale1 * target.w),
        Math.fma(scale0, y, scale1 * target.z),
        Math.fma(scale0, z, scale1 * target.y),
        Math.fma(scale0, w, scale1 * target.x)
    )
}