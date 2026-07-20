package ru.hollowhorizon.hollowengine.common.utils.math

import kotlin.math.*

object Easing {

    val linear = Easing { it }

    val easeInSine = Easing { 1f - cos((it * PI.toFloat()) / 2f) }
    val easeOutSine = Easing { sin((it * PI.toFloat()) / 2f) }
    val easeInOutSine = Easing { -(cos(PI.toFloat() * it) - 1f) / 2f }

    val easeInQuad = Easing { it * it }
    val easeOutQuad = Easing { 1f - (1f - it) * (1f - it) }
    val easeInOutQuad = Easing {
        if (it < 0.5f) 2f * it * it else 1f - (-2f * it + 2f).pow(2) / 2f
    }

    val easeInCubic = Easing { it * it * it }
    val easeOutCubic = Easing { 1f - (1f - it).pow(3) }
    val easeInOutCubic = Easing {
        if (it < 0.5f) 4f * it * it * it else 1f - (-2f * it + 2f).pow(3) / 2f
    }

    val easeInQuart = Easing { it * it * it * it }
    val easeOutQuart = Easing { 1f - (1f - it).pow(4) }
    val easeInOutQuart = Easing {
        if (it < 0.5f) 8f * it * it * it * it else 1f - (-2f * it + 2f).pow(4) / 2f
    }

    val easeInQuint = Easing { it * it * it * it * it }
    val easeOutQuint = Easing { 1f - (1f - it).pow(5) }
    val easeInOutQuint = Easing {
        if (it < 0.5f) 16f * it * it * it * it * it else 1f - (-2f * it + 2f).pow(5) / 2f
    }

    val easeInExpo = Easing { if (it == 0f) 0f else 2f.pow(10f * it - 10f) }
    val easeOutExpo = Easing { if (it == 1f) 1f else 1f - 2f.pow(-10f * it) }
    val easeInOutExpo = Easing {
        when {
            it == 0f -> 0f
            it == 1f -> 1f
            it < 0.5f -> 2f.pow(20f * it - 10f) / 2f
            else -> (2f - 2f.pow(-20f * it + 10f)) / 2f
        }
    }

    val easeInCirc = Easing { 1f - sqrt(1f - it.pow(2)) }
    val easeOutCirc = Easing { sqrt(1f - (it - 1f).pow(2)) }
    val easeInOutCirc = Easing {
        if (it < 0.5f) {
            (1f - sqrt(1f - (2f * it).pow(2))) / 2f
        } else {
            (sqrt(1f - (-2f * it + 2f).pow(2)) + 1f) / 2f
        }
    }

    private const val c1 = 1.70158f
    private const val c2 = c1 * 1.525f
    private const val c3 = c1 + 1f

    val easeInBack = Easing { c3 * it * it * it - c1 * it * it }
    val easeOutBack = Easing {
        val x = it - 1f
        1f + c3 * x.pow(3) + c1 * x.pow(2)
    }
    val easeInOutBack = Easing {
        if (it < 0.5f) {
            val x = 2f * it
            (x * x * ((c2 + 1f) * x - c2)) / 2f
        } else {
            val x = 2f * it - 2f
            (x * x * ((c2 + 1f) * x + c2) + 2f) / 2f
        }
    }

    private const val c4 = (2f * PI.toFloat()) / 3f
    private const val c5 = (2f * PI.toFloat()) / 4.5f

    val easeInElastic = Easing {
        when (it) {
            0f -> 0f
            1f -> 1f
            else -> -(2f.pow(10f * it - 10f)) * sin((it * 10f - 10.75f) * c4)
        }
    }
    val easeOutElastic = Easing {
        when (it) {
            0f -> 0f
            1f -> 1f
            else -> 2f.pow(-10f * it) * sin((it * 10f - 0.75f) * c4) + 1f
        }
    }
    val easeInOutElastic = Easing {
        when {
            it == 0f -> 0f
            it == 1f -> 1f
            it < 0.5f -> -(2f.pow(20f * it - 10f) * sin((20f * it - 11.125f) * c5)) / 2f
            else -> (2f.pow(-20f * it + 10f) * sin((20f * it - 11.125f) * c5)) / 2f + 1f
        }
    }

    private const val n1 = 7.5625f
    private const val d1 = 2.75f

    val easeOutBounce = Easing {
        var x = it
        if (x < 1f / d1) {
            n1 * x * x
        } else if (x < 2f / d1) {
            x -= 1.5f / d1
            n1 * x * x + 0.75f
        } else if (x < 2.5f / d1) {
            x -= 2.25f / d1
            n1 * x * x + 0.9375f
        } else {
            x -= 2.625f / d1
            n1 * x * x + 0.984375f
        }
    }
    val easeInBounce = Easing { 1f - easeOutBounce.eased(1f - it) }
    val easeInOutBounce = Easing {
        if (it < 0.5f) {
            (1f - easeOutBounce.eased(1f - 2f * it)) / 2f
        } else {
            (1f + easeOutBounce.eased(2f * it - 1f)) / 2f
        }
    }

    /**
     * Starts slow, ends slow, fast in the middle: `x -> smoothStep(0.0, 1.0, x)`
     */
    val smooth = Easing { smoothStep(0f, 1f, it) }

    fun linear(x: Float) = linear.eased(x)
    fun smooth(x: Float) = smooth.eased(x)

    fun easeInSine(x: Float) = easeInSine.eased(x)
    fun easeOutSine(x: Float) = easeOutSine.eased(x)
    fun easeInOutSine(x: Float) = easeInOutSine.eased(x)

    fun easeInQuad(x: Float) = easeInQuad.eased(x)
    fun easeOutQuad(x: Float) = easeOutQuad.eased(x)
    fun easeInOutQuad(x: Float) = easeInOutQuad.eased(x)

    fun easeInCubic(x: Float) = easeInCubic.eased(x)
    fun easeOutCubic(x: Float) = easeOutCubic.eased(x)
    fun easeInOutCubic(x: Float) = easeInOutCubic.eased(x)

    fun easeInQuart(x: Float) = easeInQuart.eased(x)
    fun easeOutQuart(x: Float) = easeOutQuart.eased(x)
    fun easeInOutQuart(x: Float) = easeInOutQuart.eased(x)

    fun easeInQuint(x: Float) = easeInQuint.eased(x)
    fun easeOutQuint(x: Float) = easeOutQuint.eased(x)
    fun easeInOutQuint(x: Float) = easeInOutQuint.eased(x)

    fun easeInExpo(x: Float) = easeInExpo.eased(x)
    fun easeOutExpo(x: Float) = easeOutExpo.eased(x)
    fun easeInOutExpo(x: Float) = easeInOutExpo.eased(x)

    fun easeInCirc(x: Float) = easeInCirc.eased(x)
    fun easeOutCirc(x: Float) = easeOutCirc.eased(x)
    fun easeInOutCirc(x: Float) = easeInOutCirc.eased(x)

    fun easeInBack(x: Float) = easeInBack.eased(x)
    fun easeOutBack(x: Float) = easeOutBack.eased(x)
    fun easeInOutBack(x: Float) = easeInOutBack.eased(x)

    fun easeInElastic(x: Float) = easeInElastic.eased(x)
    fun easeOutElastic(x: Float) = easeOutElastic.eased(x)
    fun easeInOutElastic(x: Float) = easeInOutElastic.eased(x)

    fun easeInBounce(x: Float) = easeInBounce.eased(x)
    fun easeOutBounce(x: Float) = easeOutBounce.eased(x)
    fun easeInOutBounce(x: Float) = easeInOutBounce.eased(x)

    fun step(steps: Int): Easing {
        require(steps > 0) { "Steps count must be greater than 0" }
        return Easing { input ->
            if (input >= 1f) return@Easing 1f
            val currentStep = (input * steps).toInt()
            currentStep.toFloat() / steps
        }
    }

    fun catmullRom(
        p0: Float = -0.5f,
        p3: Float = 1.5f,
        alpha: Float = 0.5f,
    ): Easing {
        val p1 = 0f
        val p2 = 1f

        return Easing { t ->
            if (t <= 0f) return@Easing p1
            if (t >= 1f) return@Easing p2

            val t0 = 0f
            val t1 = t0 + Math.abs(p1 - p0).pow(alpha)
            val t2 = t1 + Math.abs(p2 - p1).pow(alpha)
            val t3 = t2 + Math.abs(p3 - p2).pow(alpha)

            val globalT = t1 + t * (t2 - t1)

            val a1 = (t1 - globalT) / (t1 - t0) * p0 + (globalT - t0) / (t1 - t0) * p1
            val a2 = (t2 - globalT) / (t2 - t1) * p1 + (globalT - t1) / (t2 - t1) * p2
            val a3 = (t3 - globalT) / (t3 - t2) * p2 + (globalT - t2) / (t3 - t2) * p3

            val b1 = (t2 - globalT) / (t2 - t0) * a1 + (globalT - t0) / (t2 - t0) * a2
            val b2 = (t3 - globalT) / (t3 - t1) * a2 + (globalT - t1) / (t3 - t1) * a3

            val c = (t2 - globalT) / (t2 - t1) * b1 + (globalT - t1) / (t2 - t1) * b2

            c
        }
    }

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


    fun interface Easing {
        fun eased(input: Float): Float
    }
}

private fun smoothStep(low: Float, high: Float, x: Float): Float {
    val nx = ((x - low) / (high - low)).clamp()
    return nx * nx * (3 - 2 * nx)
}