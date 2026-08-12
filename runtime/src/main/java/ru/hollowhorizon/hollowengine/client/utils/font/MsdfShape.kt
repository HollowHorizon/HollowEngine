package ru.hollowhorizon.hollowengine.client.utils.font

import kotlin.math.max
import kotlin.math.min

/**
 * Outline geometry for the distance-field baker: the channels an edge can write to, an edge, a
 * contour, and the shape they form. What is done *with* the geometry lives next door, see
 * [colorEdges] for the channel assignment, [generateMsdf] for the field itself, and
 * [correctMsdfField] for the passes that clean it up.
 */
internal object MsdfChannel {
    const val RED = 1
    const val GREEN = 2
    const val BLUE = 4
    const val YELLOW = RED or GREEN
    const val MAGENTA = RED or BLUE
    const val CYAN = GREEN or BLUE
    const val WHITE = RED or GREEN or BLUE
}

/** Middle of three values: how a distance field's channels combine into one coverage. */
internal fun msdfMedian(a: Float, b: Float, c: Float): Float = max(min(a, b), min(max(a, b), c))

/**
 * One outline segment as a polyline. [points] holds `x, y` pairs; the first and last are the
 * segment's own endpoints, everything between comes from flattening a curve.
 */
internal class MsdfEdge(val points: FloatArray, tangents: FloatArray? = null) {
    var color: Int = MsdfChannel.WHITE

    val subSegments: Int get() = points.size / 2 - 1

    var startDirX: Float
        private set
    var startDirY: Float
        private set
    var endDirX: Float
        private set
    var endDirY: Float
        private set

    init {
        val startGiven = tangents != null && (tangents[0] != 0f || tangents[1] != 0f)
        val endGiven = tangents != null && (tangents[2] != 0f || tangents[3] != 0f)
        startDirX = if (startGiven) tangents[0] else points[2] - points[0]
        startDirY = if (startGiven) tangents[1] else points[3] - points[1]
        endDirX = if (endGiven) tangents[2] else points[points.size - 2] - points[points.size - 4]
        endDirY = if (endGiven) tangents[3] else points[points.size - 1] - points[points.size - 3]
    }

    fun reverseDirections() {
        val previousStartX = startDirX
        val previousStartY = startDirY
        startDirX = -endDirX
        startDirY = -endDirY
        endDirX = -previousStartX
        endDirY = -previousStartY
    }

    /** The point at [t] along the whole edge, walking its sub-segments. */
    fun pointAt(t: Float): FloatArray {
        val segments = subSegments
        val scaled = (t * segments).coerceIn(0f, segments.toFloat())
        val segment = scaled.toInt().coerceAtMost(segments - 1)
        val local = scaled - segment
        val index = segment * 2
        return floatArrayOf(
            points[index] + (points[index + 2] - points[index]) * local,
            points[index + 1] + (points[index + 3] - points[index + 1]) * local,
        )
    }

    /** Splits into three edges of equal parameter length, for coloring a contour that has too few. */
    fun splitInThirds(): List<MsdfEdge> = listOf(subEdge(0f, 1f / 3f), subEdge(1f / 3f, 2f / 3f), subEdge(2f / 3f, 1f))

    private fun subEdge(from: Float, to: Float): MsdfEdge {
        val segments = subSegments
        val start = pointAt(from)
        val end = pointAt(to)
        val collected = ArrayList<Float>()
        collected += start[0]
        collected += start[1]
        for (segment in 1 until segments) {
            val t = segment.toFloat() / segments
            if (t <= from || t >= to) continue
            collected += points[segment * 2]
            collected += points[segment * 2 + 1]
        }
        collected += end[0]
        collected += end[1]
        val tangents = floatArrayOf(
            if (from <= 0f) startDirX else 0f,
            if (from <= 0f) startDirY else 0f,
            if (to >= 1f) endDirX else 0f,
            if (to >= 1f) endDirY else 0f,
        )
        return MsdfEdge(collected.toFloatArray(), tangents).also { it.color = color }
    }

    val startX: Float get() = points[0]
    val startY: Float get() = points[1]
    val endX: Float get() = points[points.size - 2]
    val endY: Float get() = points[points.size - 1]
}

/** A closed loop of edges. */
internal class MsdfContour {
    val edges = ArrayList<MsdfEdge>()

    /** Twice the enclosed area, signed: positive counter-clockwise. Only its sign is used. */
    fun doubleSignedArea(): Float {
        var sum = 0f
        for (edge in edges) {
            val points = edge.points
            var index = 0
            while (index + 3 < points.size) {
                sum += points[index] * points[index + 3] - points[index + 2] * points[index + 1]
                index += 2
            }
        }
        return sum
    }

    fun reverse() {
        for (edge in edges) {
            val points = edge.points
            var head = 0
            var tail = points.size - 2
            while (head < tail) {
                val x = points[head]
                val y = points[head + 1]
                points[head] = points[tail]
                points[head + 1] = points[tail + 1]
                points[tail] = x
                points[tail + 1] = y
                head += 2
                tail -= 2
            }
            edge.reverseDirections()
        }
        edges.reverse()
    }
}

internal class MsdfShape {
    val contours = ArrayList<MsdfContour>()

    val isEmpty: Boolean get() = contours.all { it.edges.isEmpty() }

    fun orientForPositiveInside() {
        val area = contours.sumOf { it.doubleSignedArea().toDouble() }
        if (area <= 0.0) return
        contours.forEach { it.reverse() }
    }

    fun bounds(): FloatArray? {
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        for (contour in contours) {
            for (edge in contour.edges) {
                val points = edge.points
                var index = 0
                while (index < points.size) {
                    minX = minOf(minX, points[index])
                    maxX = max(maxX, points[index])
                    minY = minOf(minY, points[index + 1])
                    maxY = max(maxY, points[index + 1])
                    index += 2
                }
            }
        }
        if (minX > maxX) return null
        return floatArrayOf(minX, minY, maxX, maxY)
    }
}
