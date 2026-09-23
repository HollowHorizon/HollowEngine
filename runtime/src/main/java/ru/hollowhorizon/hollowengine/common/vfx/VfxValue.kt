package ru.hollowhorizon.hollowengine.common.vfx

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.hollowhorizon.hollowengine.common.utils.Color
import kotlin.math.abs

/**
 * What a number in an effect may be driven by.
 */
@Serializable
sealed interface VfxValue {
    /** One number, and the only shape that folds away entirely at load time. */
    @Serializable
    @SerialName("const")
    data class Const(val value: Float = 0f) : VfxValue

    /** A number picked uniformly between two bounds, once per evaluation. */
    @Serializable
    @SerialName("range")
    data class Range(val from: Float = 0f, val to: Float = 1f) : VfxValue

    /** A curve read at [source], scaled by [scale]. */
    @Serializable
    @SerialName("curve")
    data class OverTime(
        val curve: VfxCurve = VfxCurve(),
        val scale: Float = 1f,
        val source: VfxTimeSource = VfxTimeSource.LIFETIME,
    ) : VfxValue

    /** An expression in the VFX dialect, such as `p.speed * 0.5` or `d.charge`. */
    @Serializable
    @SerialName("expr")
    data class Expr(val source: String = "") : VfxValue

    companion object {
        val ZERO = Const(0f)
        val ONE = Const(1f)

        fun of(value: Float): VfxValue = when (value) {
            0f -> ZERO
            1f -> ONE
            else -> Const(value)
        }
    }
}

/**
 * What the horizontal axis of a curve means.
 */
@Serializable
enum class VfxTimeSource {
    /** Age over lifetime, 0 at birth and 1 as the particle dies. The usual choice. */
    LIFETIME,

    /** Age in seconds, for effects that should not stretch with lifetime. */
    PARTICLE_AGE,

    /** Seconds since the emitter started its current loop. */
    EMITTER_AGE,

    /** Seconds since the effect began playing. */
    EFFECT_TIME,

    /** Speed in blocks per second, for "faster means bigger" curves. */
    SPEED,
}

/** How the segment starting at a key reaches the next one; mirrors the timeline editor modes. */
@Serializable
enum class VfxInterpolation {
    CONSTANT, LINEAR, BEZIER,
}

/**
 * One key of a [VfxCurve]. Tangents are handle offsets in curve space, time and value, exactly as
 * the timeline editor stores them.
 */
@Serializable
data class VfxKey(
    val time: Float = 0f,
    val value: Float = 0f,
    val interpolation: VfxInterpolation = VfxInterpolation.BEZIER,
    @SerialName("in_time") val inTime: Float = 0f,
    @SerialName("in_value") val inValue: Float = 0f,
    @SerialName("out_time") val outTime: Float = 0f,
    @SerialName("out_value") val outValue: Float = 0f,
)

/**
 * A sampled curve.
 */
@Serializable
data class VfxCurve(val keys: List<VfxKey> = emptyList()) {
    /** The value at [time]; flat before the first key and after the last, [fallback] when empty. */
    fun valueAt(time: Float, fallback: Float = 0f): Float {
        if (keys.isEmpty()) return fallback
        val first = keys.first()
        if (keys.size == 1 || time <= first.time) return first.value
        val last = keys.last()
        if (time >= last.time) return last.value

        var index = 0
        while (index < keys.lastIndex && keys[index + 1].time <= time) index++
        val start = keys[index]
        val end = keys[index + 1]
        val span = end.time - start.time
        if (span <= EPSILON) return end.value

        return when (start.interpolation) {
            VfxInterpolation.CONSTANT -> start.value
            VfxInterpolation.LINEAR -> start.value + (end.value - start.value) * ((time - start.time) / span)
            VfxInterpolation.BEZIER -> bezier(start, end, span, time)
        }
    }

    private fun bezier(start: VfxKey, end: VfxKey, span: Float, time: Float): Float {
        val outLength = start.outTime.coerceAtLeast(0f)
        val inLength = (-end.inTime).coerceAtLeast(0f)
        val total = outLength + inLength
        val scale = if (total > span) span / total else 1f

        val p1 = start.value + start.outValue * scale
        val p2 = end.value + end.inValue * scale
        val x1 = start.time + outLength * scale
        val x2 = end.time - inLength * scale

        return cubic(start.value, p1, p2, end.value, solve(start.time, x1, x2, end.time, time))
    }

    private fun solve(x0: Float, x1: Float, x2: Float, x3: Float, time: Float): Float {
        var s = ((time - x0) / (x3 - x0)).coerceIn(0f, 1f)
        repeat(NEWTON_STEPS) {
            val error = cubic(x0, x1, x2, x3, s) - time
            if (abs(error) <= EPSILON) return s
            val slope = slope(x0, x1, x2, x3, s)
            if (abs(slope) <= EPSILON) return@repeat
            s = (s - error / slope).coerceIn(0f, 1f)
        }
        var low = 0f
        var high = 1f
        repeat(BISECTION_STEPS) {
            s = (low + high) * 0.5f
            if (cubic(x0, x1, x2, x3, s) < time) low = s else high = s
        }
        return s
    }

    private fun cubic(a: Float, b: Float, c: Float, d: Float, s: Float): Float {
        val inverse = 1f - s
        return inverse * inverse * inverse * a + 3f * inverse * inverse * s * b + 3f * inverse * s * s * c + s * s * s * d
    }

    private fun slope(a: Float, b: Float, c: Float, d: Float, s: Float): Float {
        val inverse = 1f - s
        return 3f * inverse * inverse * (b - a) + 6f * inverse * s * (c - b) + 3f * s * s * (d - c)
    }

    companion object {
        private const val EPSILON = 1.0e-5f
        private const val NEWTON_STEPS = 8
        private const val BISECTION_STEPS = 24

        /** A curve that holds [value] for its whole span. */
        fun flat(value: Float): VfxCurve = VfxCurve(listOf(VfxKey(0f, value), VfxKey(1f, value)))

        fun ramp(from: Float, to: Float): VfxCurve = VfxCurve(listOf(VfxKey(0f, from), VfxKey(1f, to)))
    }
}

/** A color as the file stores it. */
@Serializable
data class VfxRgba(val r: Float = 1f, val g: Float = 1f, val b: Float = 1f, val a: Float = 1f) {

    companion object {
        val WHITE = VfxRgba()

        fun of(color: Color) = VfxRgba(color.r, color.g, color.b, color.a)
    }
}

@Serializable
data class VfxGradientStop(val position: Float = 0f, val color: VfxRgba = VfxRgba.WHITE)

/** Color over a normalized axis. Stops are kept sorted by the editor and sampled linearly. */
@Serializable
data class VfxGradient(val stops: List<VfxGradientStop> = emptyList()) {
    fun colorAt(position: Float, fallback: VfxRgba = VfxRgba.WHITE): VfxRgba {
        if (stops.isEmpty()) return fallback
        val first = stops.first()
        if (stops.size == 1 || position <= first.position) return first.color
        val last = stops.last()
        if (position >= last.position) return last.color

        var index = 0
        while (index < stops.lastIndex && stops[index + 1].position <= position) index++
        val start = stops[index]
        val end = stops[index + 1]
        val span = end.position - start.position
        if (span <= 1.0e-5f) return end.color

        val t = (position - start.position) / span
        return VfxRgba(
            start.color.r + (end.color.r - start.color.r) * t,
            start.color.g + (end.color.g - start.color.g) * t,
            start.color.b + (end.color.b - start.color.b) * t,
            start.color.a + (end.color.a - start.color.a) * t,
        )
    }

    companion object {
        fun fade(color: VfxRgba = VfxRgba.WHITE) = VfxGradient(
            listOf(
                VfxGradientStop(0f, color),
                VfxGradientStop(1f, color.copy(a = 0f)),
            )
        )
    }
}

/** Three [VfxValue]s that together make a vector: an offset, a velocity, a rotation, a size. */
@Serializable
data class VfxVec3Value(
    val x: VfxValue = VfxValue.ZERO,
    val y: VfxValue = VfxValue.ZERO,
    val z: VfxValue = VfxValue.ZERO,
) {
    companion object {
        val ZERO = VfxVec3Value()
        val ONE = VfxVec3Value(VfxValue.ONE, VfxValue.ONE, VfxValue.ONE)

        fun of(x: Float, y: Float, z: Float) = VfxVec3Value(VfxValue.of(x), VfxValue.of(y), VfxValue.of(z))

        fun all(value: Float) = of(value, value, value)
    }
}

/** How a color property is authored. */
@Serializable
sealed interface VfxColorValue {
    @Serializable
    @SerialName("solid")
    data class Solid(val color: VfxRgba = VfxRgba.WHITE) : VfxColorValue

    @Serializable
    @SerialName("gradient")
    data class Gradient(
        val gradient: VfxGradient = VfxGradient.fade(),
        val source: VfxTimeSource = VfxTimeSource.LIFETIME,
    ) : VfxColorValue

    /** Per-channel values, for colors that follow something the other shapes cannot reach. */
    @Serializable
    @SerialName("channels")
    data class Channels(
        val r: VfxValue = VfxValue.ONE,
        val g: VfxValue = VfxValue.ONE,
        val b: VfxValue = VfxValue.ONE,
        val a: VfxValue = VfxValue.ONE,
    ) : VfxColorValue

    companion object {
        val WHITE = Solid()
    }
}

fun VfxValue.constantOr(fallback: Float): Float = when (this) {
    is VfxValue.Const -> value
    is VfxValue.Range -> (from + to) * 0.5f
    is VfxValue.OverTime -> curve.valueAt(0f, fallback) * scale
    is VfxValue.Expr -> fallback
}

fun VfxVec3Value.constants(fallback: Float = 0f): FloatArray =
    floatArrayOf(x.constantOr(fallback), y.constantOr(fallback), z.constantOr(fallback))

fun VfxColorValue.constants(): FloatArray = when (this) {
    is VfxColorValue.Solid -> floatArrayOf(color.r, color.g, color.b, color.a)
    is VfxColorValue.Gradient -> gradient.colorAt(0f).let { floatArrayOf(it.r, it.g, it.b, it.a) }
    is VfxColorValue.Channels -> floatArrayOf(r.constantOr(1f), g.constantOr(1f), b.constantOr(1f), a.constantOr(1f))
}
