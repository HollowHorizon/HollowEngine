package ru.hollowhorizon.hollowengine.client.ui.ide.timeline

import kotlin.math.abs
import kotlin.math.sign
import kotlin.math.sqrt

/** How the segment that starts at a keyframe reaches the next one. */
enum class KeyInterpolation {
    /** Holds the value until the next key, then jumps (step interpolation). */
    CONSTANT,

    /** A straight line, no handles involved (linear interpolation). */
    LINEAR,

    /** Follows the cubic spline described by the two keys' facing handles. */
    BEZIER,
}

/** How a keyframe's two handles relate to each other while one of them is dragged. */
enum class HandleMode {
    /** Derived from the neighboring keys, flattened at local extrema so the curve never overshoots. */
    AUTO,

    /** The partner mirrors both the direction and the length of the handle being dragged. */
    MIRRORED,

    /** The partner follows the direction only, keeping the length it already had. */
    ALIGNED,

    /** Both handles move independently, so the curve can break at the key. */
    FREE,
}

data class KeyTangent(val time: Float, val value: Float) {
    companion object {
        val ZERO = KeyTangent(0f, 0f)
    }
}

data class ChannelTangents(
    val incoming: KeyTangent = KeyTangent.ZERO,
    val outgoing: KeyTangent = KeyTangent.ZERO,
) {
    fun tangent(side: TangentSide): KeyTangent = when (side) {
        TangentSide.INCOMING -> incoming
        TangentSide.OUTGOING -> outgoing
    }
}

data class CurvePreset(
    val id: String,
    val category: String,
    val name: String,
    val interpolation: KeyInterpolation = KeyInterpolation.BEZIER,
    val outX: Float = 0f,
    val outY: Float = 0f,
    val inX: Float = 1f,
    val inY: Float = 1f,
)

object TimelineCurve {
    private const val NEWTON_STEPS = 8
    private const val BISECTION_STEPS = 24
    private const val SOLVE_EPSILON = 1e-5f

    fun sampleSegment(
        startTime: Float,
        startValue: Float,
        outgoing: KeyTangent,
        endTime: Float,
        endValue: Float,
        incoming: KeyTangent,
        time: Float,
    ): Float {
        val span = endTime - startTime
        if (span <= SOLVE_EPSILON) return endValue
        if (time <= startTime) return startValue
        if (time >= endTime) return endValue

        val outLength = outgoing.time.coerceAtLeast(0f)
        val inLength = (-incoming.time).coerceAtLeast(0f)
        val total = outLength + inLength
        val scale = if (total > span) span / total else 1f

        val p0 = startValue
        val p1 = startValue + outgoing.value * scale
        val p2 = endValue + incoming.value * scale
        val p3 = endValue
        val x1 = startTime + outLength * scale
        val x2 = endTime - inLength * scale

        val s = solveForTime(startTime, x1, x2, endTime, time)
        return cubic(p0, p1, p2, p3, s)
    }

    fun autoTangents(
        previousTime: Float?,
        previousValue: Float?,
        time: Float,
        value: Float,
        nextTime: Float?,
        nextValue: Float?,
    ): ChannelTangents {
        val backSpan = previousTime?.let { (time - it).coerceAtLeast(0f) }
        val frontSpan = nextTime?.let { (it - time).coerceAtLeast(0f) }
        val slope = if (previousTime != null && previousValue != null && nextTime != null && nextValue != null) {
            val rising = value - previousValue
            val falling = nextValue - value
            val span = nextTime - previousTime
            if (span <= SOLVE_EPSILON || rising == 0f || falling == 0f || sign(rising) != sign(falling)) 0f
            else (nextValue - previousValue) / span
        } else {
            0f
        }
        val inLength = (backSpan ?: frontSpan ?: 0f) / 3f
        val outLength = (frontSpan ?: backSpan ?: 0f) / 3f
        return ChannelTangents(
            incoming = KeyTangent(-inLength, -slope * inLength),
            outgoing = KeyTangent(outLength, slope * outLength),
        )
    }

    fun mirror(
        dragged: KeyTangent,
        partner: KeyTangent,
        timeScale: Float,
        valueScale: Float,
        mirrorLength: Boolean,
    ): KeyTangent? {
        val dx = dragged.time * timeScale
        val dy = dragged.value * valueScale
        val draggedLength = sqrt(dx * dx + dy * dy)
        if (draggedLength <= SOLVE_EPSILON) return null
        if (mirrorLength) return KeyTangent(-dragged.time, -dragged.value)

        val px = partner.time * timeScale
        val py = partner.value * valueScale
        val partnerLength = sqrt(px * px + py * py)
        val factor = -partnerLength / draggedLength
        return KeyTangent(dragged.time * factor, dragged.value * factor)
    }

    private fun solveForTime(x0: Float, x1: Float, x2: Float, x3: Float, time: Float): Float {
        var s = ((time - x0) / (x3 - x0)).coerceIn(0f, 1f)
        repeat(NEWTON_STEPS) {
            val error = cubic(x0, x1, x2, x3, s) - time
            if (abs(error) <= SOLVE_EPSILON) return s
            val slope = cubicSlope(x0, x1, x2, x3, s)
            if (abs(slope) <= SOLVE_EPSILON) return@repeat
            s = (s - error / slope).coerceIn(0f, 1f)
        }
        if (abs(cubic(x0, x1, x2, x3, s) - time) <= SOLVE_EPSILON) return s

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

    private fun cubicSlope(a: Float, b: Float, c: Float, d: Float, s: Float): Float {
        val inverse = 1f - s
        return 3f * inverse * inverse * (b - a) + 6f * inverse * s * (c - b) + 3f * s * s * (d - c)
    }
}

object CurvePresets {
    val all: List<CurvePreset> = listOf(
        CurvePreset("hold", "Basic", "Hold", KeyInterpolation.CONSTANT),
        CurvePreset("linear", "Basic", "Linear", KeyInterpolation.LINEAR),
        CurvePreset("smooth", "Basic", "Smooth", outX = 0.25f, outY = 0.1f, inX = 0.25f, inY = 1f),

        CurvePreset("sineIn", "Sine", "In", outX = 0.12f, outY = 0f, inX = 0.39f, inY = 0f),
        CurvePreset("sineOut", "Sine", "Out", outX = 0.61f, outY = 1f, inX = 0.88f, inY = 1f),
        CurvePreset("sineInOut", "Sine", "In Out", outX = 0.37f, outY = 0f, inX = 0.63f, inY = 1f),

        CurvePreset("quadIn", "Quad", "In", outX = 0.11f, outY = 0f, inX = 0.5f, inY = 0f),
        CurvePreset("quadOut", "Quad", "Out", outX = 0.5f, outY = 1f, inX = 0.89f, inY = 1f),
        CurvePreset("quadInOut", "Quad", "In Out", outX = 0.45f, outY = 0f, inX = 0.55f, inY = 1f),

        CurvePreset("cubicIn", "Cubic", "In", outX = 0.32f, outY = 0f, inX = 0.67f, inY = 0f),
        CurvePreset("cubicOut", "Cubic", "Out", outX = 0.33f, outY = 1f, inX = 0.68f, inY = 1f),
        CurvePreset("cubicInOut", "Cubic", "In Out", outX = 0.65f, outY = 0f, inX = 0.35f, inY = 1f),

        CurvePreset("quartIn", "Quart", "In", outX = 0.5f, outY = 0f, inX = 0.75f, inY = 0f),
        CurvePreset("quartOut", "Quart", "Out", outX = 0.25f, outY = 1f, inX = 0.5f, inY = 1f),
        CurvePreset("quartInOut", "Quart", "In Out", outX = 0.76f, outY = 0f, inX = 0.24f, inY = 1f),

        CurvePreset("quintIn", "Quint", "In", outX = 0.64f, outY = 0f, inX = 0.78f, inY = 0f),
        CurvePreset("quintOut", "Quint", "Out", outX = 0.22f, outY = 1f, inX = 0.36f, inY = 1f),
        CurvePreset("quintInOut", "Quint", "In Out", outX = 0.83f, outY = 0f, inX = 0.17f, inY = 1f),

        CurvePreset("expoIn", "Expo", "In", outX = 0.7f, outY = 0f, inX = 0.84f, inY = 0f),
        CurvePreset("expoOut", "Expo", "Out", outX = 0.16f, outY = 1f, inX = 0.3f, inY = 1f),
        CurvePreset("expoInOut", "Expo", "In Out", outX = 0.87f, outY = 0f, inX = 0.13f, inY = 1f),

        CurvePreset("circIn", "Circ", "In", outX = 0.55f, outY = 0f, inX = 1f, inY = 0.45f),
        CurvePreset("circOut", "Circ", "Out", outX = 0f, outY = 0.55f, inX = 0.45f, inY = 1f),
        CurvePreset("circInOut", "Circ", "In Out", outX = 0.85f, outY = 0f, inX = 0.15f, inY = 1f),

        CurvePreset("backIn", "Back", "In", outX = 0.36f, outY = 0f, inX = 0.66f, inY = -0.56f),
        CurvePreset("backOut", "Back", "Out", outX = 0.34f, outY = 1.56f, inX = 0.64f, inY = 1f),
        CurvePreset("backInOut", "Back", "In Out", outX = 0.68f, outY = -0.6f, inX = 0.32f, inY = 1.6f),
    )

    val categories: List<String> = all.map { it.category }.distinct()

    fun byId(id: String): CurvePreset? = all.firstOrNull { it.id == id }

    fun of(category: String): List<CurvePreset> = all.filter { it.category == category }

    fun apply(preset: CurvePreset, start: Keyframe, end: Keyframe?) {
        start.interpolation = preset.interpolation
        if (preset.interpolation != KeyInterpolation.BEZIER || end == null) return

        val span = end.time - start.time
        val delta = end.value - start.value
        start.handleMode = HandleMode.FREE
        end.handleMode = HandleMode.FREE
        start.outgoing = KeyTangent(span * preset.outX, delta * preset.outY)
        end.incoming = KeyTangent(-span * (1f - preset.inX), -delta * (1f - preset.inY))
    }

    fun match(start: Keyframe, end: Keyframe?): CurvePreset? {
        if (start.interpolation == KeyInterpolation.CONSTANT) return byId("hold")
        if (start.interpolation == KeyInterpolation.LINEAR) return byId("linear")
        if (end == null) return null
        val span = end.time - start.time
        val delta = end.value - start.value
        if (span <= 0f) return null
        return all.firstOrNull { preset ->
            preset.interpolation == KeyInterpolation.BEZIER && matches(
                start.outgoing.time, span * preset.outX
            ) && matches(start.outgoing.value, delta * preset.outY) && matches(
                end.incoming.time, -span * (1f - preset.inX)
            ) && matches(end.incoming.value, -delta * (1f - preset.inY))
        }
    }

    private fun matches(actual: Float, expected: Float): Boolean = abs(actual - expected) <= 0.001f
}
