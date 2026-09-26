package ru.hollowhorizon.hollowengine.client.ui.graph

import kotlin.math.abs
import kotlin.math.hypot

/** A rectangle of the graph or of the canvas, whichever space it was made in. */
data class GraphRect(val x: Float, val y: Float, val width: Float, val height: Float) {
    val centerX get() = x + width / 2f
    val centerY get() = y + height / 2f

    fun contains(px: Float, py: Float): Boolean = px >= x && px <= x + width && py >= y && py <= y + height

    /** This rectangle of the graph as it lies on the canvas. */
    fun toCanvas(view: GraphViewState) =
        GraphRect(view.toCanvasX(x), view.toCanvasY(y), width * view.zoom, height * view.zoom)
}

/** A connection as a sampled curve in canvas space: what gets drawn, clicked and labeled. */
class GraphCurve(val points: FloatArray) {
    val pointCount: Int get() = points.size / 2

    /** A point [fraction] of the way along the curve. */
    fun pointAt(fraction: Float): FloatArray {
        val last = pointCount - 1
        val step = (fraction.coerceIn(0f, 1f) * last).toInt().coerceIn(0, last)
        return floatArrayOf(points[step * 2], points[step * 2 + 1])
    }

    fun distanceTo(x: Float, y: Float): Float = (0 until pointCount - 1).minOfOrNull { index ->
        distanceToSegment(
            x, y,
            points[index * 2], points[index * 2 + 1],
            points[index * 2 + 2], points[index * 2 + 3],
        )
    } ?: Float.MAX_VALUE

    override fun equals(other: Any?) = other is GraphCurve && points.contentEquals(other.points)
    override fun hashCode() = points.contentHashCode()
}

/** How the two kinds of graph link their nodes. */
object GraphCurves {
    private const val SAMPLES = 32
    private const val MIN_BOW = 26f
    private const val MAX_BOW = 220f

    /**
     * A link from a side of [from] into a side of [to].
     */
    fun betweenBoxes(from: GraphRect, to: GraphRect, offset: Float = 0f, gap: Float = 0f): GraphCurve {
        val horizontal = abs(to.centerX - from.centerX) >= abs(to.centerY - from.centerY)
        val start = from.sidePoint(towards = to, horizontal = horizontal, along = offset, gap = 0f)
        val end = to.sidePoint(towards = from, horizontal = horizontal, along = offset, gap = gap)

        val reach = if (horizontal) abs(end[0] - start[0]) else abs(end[1] - start[1])
        val bow = (reach * 0.6f).coerceIn(MIN_BOW, MAX_BOW)
        return bezier(
            start[0], start[1],
            start[0] + if (horizontal) bow * start[2] else 0f, start[1] + if (horizontal) 0f else bow * start[3],
            end[0] + if (horizontal) bow * end[2] else 0f, end[1] + if (horizontal) 0f else bow * end[3],
            end[0], end[1],
        )
    }

    /**
     * A link from an output pin to an input pin.
     */
    fun betweenPins(fromX: Float, fromY: Float, toX: Float, toY: Float, zoom: Float): GraphCurve {
        val bow = (abs(toX - fromX) * 0.5f).coerceIn(MIN_BOW * zoom, MAX_BOW * zoom)
        return bezier(fromX, fromY, fromX + bow, fromY, toX - bow, toY, toX, toY)
    }

    /** The key of the curve closest to ([x], [y]) within [radius], if any is. */
    fun <K> nearest(curves: List<Pair<K, GraphCurve>>, x: Float, y: Float, radius: Float): K? =
        curves.map { (key, curve) -> key to curve.distanceTo(x, y) }
            .filter { it.second <= radius }
            .minByOrNull { it.second }?.first

    private fun bezier(
        x0: Float, y0: Float,
        x1: Float, y1: Float,
        x2: Float, y2: Float,
        x3: Float, y3: Float,
    ): GraphCurve {
        val points = FloatArray((SAMPLES + 1) * 2)
        for (step in 0..SAMPLES) {
            val t = step / SAMPLES.toFloat()
            val inverse = 1f - t
            val a = inverse * inverse * inverse
            val b = 3f * inverse * inverse * t
            val c = 3f * inverse * t * t
            val d = t * t * t
            points[step * 2] = a * x0 + b * x1 + c * x2 + d * x3
            points[step * 2 + 1] = a * y0 + b * y1 + c * y2 + d * y3
        }
        return GraphCurve(points)
    }

    /** Where a link attaches to this box: x, y, then the direction it leaves in. */
    private fun GraphRect.sidePoint(towards: GraphRect, horizontal: Boolean, along: Float, gap: Float): FloatArray =
        if (horizontal) {
            val right = towards.centerX >= centerX
            floatArrayOf(
                if (right) x + width + gap else x - gap,
                (centerY + along).coerceIn(y + 4f, y + height - 4f),
                if (right) 1f else -1f,
                0f,
            )
        } else {
            val below = towards.centerY >= centerY
            floatArrayOf(
                (centerX + along).coerceIn(x + 4f, x + width - 4f),
                if (below) y + height + gap else y - gap,
                0f,
                if (below) 1f else -1f,
            )
        }
}

internal fun distanceToSegment(x: Float, y: Float, x1: Float, y1: Float, x2: Float, y2: Float): Float {
    val dx = x2 - x1
    val dy = y2 - y1
    val lengthSquared = dx * dx + dy * dy
    if (lengthSquared == 0f) return hypot(x - x1, y - y1)
    val t = (((x - x1) * dx + (y - y1) * dy) / lengthSquared).coerceIn(0f, 1f)
    return hypot(x - (x1 + t * dx), y - (y1 + t * dy))
}

/** The part of the segment inside a [width] by [height] box at the origin, or null when none is. */
internal fun clipSegment(x1: Float, y1: Float, x2: Float, y2: Float, width: Float, height: Float): FloatArray? {
    val dx = x2 - x1
    val dy = y2 - y1
    var enter = 0f
    var exit = 1f

    val checks = arrayOf(-dx to x1, dx to (width - x1), -dy to y1, dy to (height - y1))
    checks.forEach { (p, q) ->
        if (p == 0f) {
            if (q < 0f) return null
        } else {
            val r = q / p
            if (p < 0f) {
                if (r > exit) return null
                if (r > enter) enter = r
            } else {
                if (r < enter) return null
                if (r < exit) exit = r
            }
        }
    }
    return floatArrayOf(x1 + enter * dx, y1 + enter * dy, x1 + exit * dx, y1 + exit * dy)
}
